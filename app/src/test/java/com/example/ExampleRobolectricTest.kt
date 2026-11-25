package com.example

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiImageResult
import com.example.data.ai.AiRepository
import com.example.data.ai.AiStreamEvent
import com.example.data.ai.AttachmentPayload
import com.example.data.ai.RateLimiter
import com.example.data.local.AppDatabase
import com.example.data.local.MessageEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.ChatDepRepository
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var repository: ChatDepRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Executors.newSingleThreadExecutor())
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        prefsRepo = UserPreferencesRepository(context)
        repository = ChatDepRepository(db.chatDepDao(), prefsRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun awaitMainAndIo(
        timeoutMs: Long = 3_000L,
        condition: suspend () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (condition()) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                return
            }
            Thread.sleep(15)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("La condición asíncrona no se completó dentro de ${timeoutMs}ms", condition())
    }

    @Test
    fun `app_name es ChatDep y recaptcha_site_key esta configurado`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("ChatDep", appName)

        val resId = context.resources.getIdentifier("recaptcha_site_key", "string", context.packageName)
        assertTrue(resId != 0)
        assertTrue(context.getString(resId).isNotBlank())
    }

    @Test
    fun `MainActivity inicia sin errores con App Check configurado`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val activity = controller.get()
        assertNotNull(activity)
        assertFalse(activity.isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun `registro login historial busqueda renombrar y eliminar chat`() = runBlocking {
        val regResult = repository.registerUser("Ana López", "ana@chatdep.ia", "clave123")
        assertTrue(regResult.isSuccess)
        val user = regResult.getOrThrow()

        val badLogin = repository.loginUser("ana@chatdep.ia", "incorrecta")
        assertTrue(badLogin.isFailure)

        val loginResult = repository.loginUser("ana@chatdep.ia", "clave123")
        assertTrue(loginResult.isSuccess)

        val convo = repository.createConversation(user.id, "Plan de estudio Kotlin")
        repository.insertMessage(
            MessageEntity(
                id = "m1",
                conversationId = convo.id,
                role = "user",
                content = "Explícame Jetpack Compose"
            )
        )

        val found = repository.observeConversations(user.id, "Compose").first()
        assertEquals(1, found.size)
        assertEquals("Plan de estudio Kotlin", found.first().title)

        repository.renameConversation(convo.id, "Compose Avanzado")
        val renamed = repository.observeConversations(user.id, "").first()
        assertEquals("Compose Avanzado", renamed.first().title)

        repository.deleteConversation(convo.id)
        val afterDelete = repository.observeConversations(user.id, "").first()
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun `ChatViewModel conserva contexto multi turno edita y regenera`() = runBlocking {
        val user = repository.registerUser("Carlos", "carlos@chatdep.ia", "secreto9").getOrThrow()
        var capturedHistorySize = -1
        var capturedPrompt = ""
        var replyCounter = 0

        val testAi = object : AiRepository {
            override fun streamChatResponse(
                priorHistory: List<MessageEntity>,
                userPrompt: String,
                attachment: AttachmentPayload?
            ): Flow<AiStreamEvent> = flow {
                capturedHistorySize = priorHistory.size
                capturedPrompt = userPrompt
                replyCounter += 1
                emit(AiStreamEvent.Token("Hola "))
                emit(AiStreamEvent.Completed("Respuesta Gemini #$replyCounter"))
            }

            override suspend fun generateImage(
                prompt: String,
                aspectRatio: String,
                style: String
            ): AiImageResult {
                val f = File(context.cacheDir, "test_img.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
                return AiImageResult(f.absolutePath, "Imagen generada con Gemini")
            }
        }

        val vm = ChatViewModel(user.id, repository, testAi)
        vm.sendMessage("Primera pregunta")
        awaitMainAndIo {
            val id = vm.activeConversationId.value
            id != null && !vm.isGenerating.value && repository.getMessagesOnce(id).size == 2
        }

        val convoId = vm.activeConversationId.value
        assertNotNull(convoId)
        assertEquals(0, capturedHistorySize)

        vm.sendMessage("Segunda pregunta")
        awaitMainAndIo {
            !vm.isGenerating.value && repository.getMessagesOnce(convoId!!).size == 4
        }
        assertEquals(2, capturedHistorySize)
        assertEquals("Segunda pregunta", capturedPrompt)

        val allAfterSecond = repository.getMessagesOnce(convoId!!)
        val secondAssistant = allAfterSecond.last()

        // Regenerar última respuesta mantiene la pregunta y reemplaza solo la respuesta
        vm.regenerateResponse(secondAssistant)
        awaitMainAndIo {
            val msgs = repository.getMessagesOnce(convoId)
            !vm.isGenerating.value && msgs.size == 4 && msgs.last().content == "Respuesta Gemini #3"
        }

        // Editar primer mensaje limpia mensajes posteriores y regenera desde el turno editado
        vm.editUserMessage(allAfterSecond.first(), "Primera pregunta editada")
        awaitMainAndIo {
            val msgs = repository.getMessagesOnce(convoId)
            !vm.isGenerating.value && msgs.size == 2 && msgs.last().content == "Respuesta Gemini #4"
        }
        val afterEdit = repository.getMessagesOnce(convoId)
        assertEquals(2, afterEdit.size)
        assertEquals("Primera pregunta editada", afterEdit.first().content)

        // Generación de imagen en estudio guarda archivo e historial en Room
        vm.generateImageRequest("Un robot leyendo un libro", "1:1", "Fotorrealista")
        awaitMainAndIo {
            !vm.isGeneratingImage.value && repository.observeImageRequests(user.id).first().isNotEmpty()
        }
        val imgRequests = repository.observeImageRequests(user.id).first()
        assertEquals(1, imgRequests.size)
        assertNotNull(imgRequests.first().imagePath)
    }

    @Test
    fun `ChatViewModel detener generacion conserva texto parcial`() = runBlocking {
        val user = repository.registerUser("Lucía", "lucia@chatdep.ia", "clave999").getOrThrow()
        val slowAi = object : AiRepository {
            override fun streamChatResponse(
                priorHistory: List<MessageEntity>,
                userPrompt: String,
                attachment: AttachmentPayload?
            ): Flow<AiStreamEvent> = flow {
                emit(AiStreamEvent.Token("Texto parcial importante"))
                delay(10_000L)
                emit(AiStreamEvent.Completed("Texto completo"))
            }

            override suspend fun generateImage(
                prompt: String,
                aspectRatio: String,
                style: String
            ): AiImageResult = AiImageResult("", "")
        }

        val vm = ChatViewModel(user.id, repository, slowAi)
        vm.sendMessage("Explica algo largo")
        awaitMainAndIo {
            vm.isGenerating.value && vm.streamingText.value.isNotEmpty()
        }

        vm.stopGeneration()
        val convoId = vm.activeConversationId.value!!
        awaitMainAndIo {
            !vm.isGenerating.value && repository.getMessagesOnce(convoId).size == 2
        }

        val saved = repository.getMessagesOnce(convoId)
        assertTrue(saved.last().content.contains("Texto parcial importante"))
        assertTrue(saved.last().content.contains("Generación detenida"))
    }

    @Test
    fun `RateLimiter previene rafagas y permite pausas normales`() {
        val limiter = RateLimiter(minIntervalMs = 1_000L, maxPerMinute = 3, maxPerHour = 10)
        assertTrue(limiter.checkAndRecord(10_000L).isSuccess)
        assertFalse(limiter.checkAndRecord(10_200L).isSuccess) // cooldown
        assertTrue(limiter.checkAndRecord(11_500L).isSuccess)
        assertTrue(limiter.checkAndRecord(13_000L).isSuccess)
        assertFalse(limiter.checkAndRecord(14_500L).isSuccess) // maxPerMinute = 3
    }

    @Test
    fun `DataStore cambia modo oscuro y claro correctamente`() = runBlocking {
        prefsRepo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, prefsRepo.preferencesFlow.first().themeMode)

        prefsRepo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, prefsRepo.preferencesFlow.first().themeMode)
    }
}
