# ChatDep — Tu IA Inteligente, Siempre Contigo

Aplicación Android nativa (`com.chatdep.ia`) desarrollada en **Kotlin**, **Jetpack Compose**, **Material 3**, **Room** y **Firebase AI Logic (Google Gemini)** con **Firebase App Check**.

## Descargar e instalar el APK desde GitHub

Este repositorio incluye un flujo automático de **GitHub Actions** (`.github/workflows/build-apk.yml`) que ejecuta las pruebas y compila automáticamente los APK en cada subida a `main` / `master` o cuando lo ejecutes manualmente desde la pestaña **Actions**.

### Pasos en GitHub
1. Sube este proyecto a tu repositorio en GitHub (por ejemplo en la rama `main`).
2. Entra en la pestaña **Actions** de tu repositorio y verifica que termine el workflow **"Compilar e Instalar ChatDep APK"** (si no se inició solo, pulsa **Run workflow**).
3. Abre la sección **Releases** en la página principal de tu repositorio (o el apartado *Artifacts* del workflow).
4. Descarga **`ChatDep-release.apk`** directamente en cualquier teléfono Android y abre el archivo para instalarlo (habilitando *"Instalar aplicaciones desconocidas"* si Android lo solicita).

> **Recomendación para varios dispositivos:** Usa **`ChatDep-release.apk`**, ya que activa **reCAPTCHA Enterprise** en Firebase App Check y funciona en cualquier dispositivo Android sin registrar tokens de depuración.

## Firma permanente de producción (opcional pero recomendada)

Si deseas que futuras actualizaciones del APK mantengan siempre exactamente la misma firma (y puedas actualizar encima sin desinstalar la versión anterior), agrega estos **3 Repository Secrets** en **Settings > Secrets and variables > Actions** de tu repositorio en GitHub:

- `RELEASE_KEYSTORE_BASE64`: tu archivo `.jks` (con alias `upload`) convertido a Base64 (`base64 -w 0 my-upload-key.jks`).
- `STORE_PASSWORD`: contraseña del keystore.
- `KEY_PASSWORD`: contraseña de la clave `upload`.

Si no configuras estos secretos todavía, GitHub Actions igualmente generará un `ChatDep-release.apk` funcional e instalable usando una firma temporal de CI.
