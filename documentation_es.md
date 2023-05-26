##### Instalación
Puedes instalar ZeroBounceSDK agregando la dependencia a tu archivo Gradle:

```gradle
implementation 'com.zerobounce.android:zerobouncesdk:1.1.4'
```

##### Uso
Importa el SDK en tu archivo:

```kotlin
import com.zerobounce.android.ZeroBounceSDK
```

Inicializa el SDK con tu clave de API:

```kotlin
ZeroBounceSDK.initialize("<TU_CLAVE_DE_API>")
```

##### Ejemplos
Luego puedes utilizar cualquiera de los métodos del SDK. Aquí tienes algunos ejemplos:

* ####### Validar una dirección de correo electrónico
    ```kotlin
    ZeroBounceSDK.validate(
        "<CORREO_A_VALIDAR>",
        "<DIRECCIÓN_IP_OPCIONAL>",
        { rsp ->
            Log.d("MainActivity", "validate rsp: $rsp")
            // Tu implementación
        },
        { error ->
            Log.e("MainActivity", "validate error: $error")
            // Tu implementación
        }
    )
    ```

* ####### Validar una lista de direcciones de correo electrónico en lote
    ```kotlin
    val emailsData = listOf(
        ZBValidateBatchData(email = "valid@example.com", ip = "1.1.1.1"),
        ZBValidateBatchData(email = "invalid@example.com", ip = "1.1.1.1"),
        ZBValidateBatchData(email = "disposable@example.com", ip = null)
    )
    ZeroBounceSDK.validateBatch(
        emailsData,
        { rsp ->
            Log.d("MainActivity", "validateBatch rsp: $rsp")
            // Tu implementación
        },
        { error ->
            Log.e("MainActivity", "validateBatch error: $error")
            // Tu implementación
        }
    )
    ```

* ####### Verificar cuántos créditos te quedan en tu cuenta
    ```kotlin
    ZeroBounceSDK.getCredits(
        { rsp -> 
            Log.d("MainActivity", "getCredits rsp: $rsp")
            // Tu implementación
        },
        { error -> 
            Log.e("MainActivity", "getCredits error: $error") 
            // Tu implementación
        }
    )
    ```

* ####### Verificar el uso de tu API en un período de tiempo determinado
    ```kotlin
    // Importa java.time.LocalDate
    val startDate = LocalDate.now()    // La fecha de inicio para ver el uso de la API
    val endDate = LocalDate.now()      // La fecha de fin para ver el uso de la API

    ZeroBounceSDK.getApiUsage(
        startDate, 
        endDate,
        { rsp -> 
            Log.d("MainActivity", "getApiUsage rsp: $rsp")
            // Tu implementación
        },
        { error -> 
            Log.e("MainActivity", "getApiUsage error: $error") 
            // Tu implementación
        }
    )
    ```

* ####### El método *sendFile* permite enviar un archivo para validación masiva de correos electrónicos
    ```kotlin
    // Importa java.io.File
    val myFile = File("<RUTA_DEL_ARCHIVO>")  // El archivo en formato CSV o TXT
    val emailAddressColumn = 3        // El índice de la columna de dirección de correo electrónico en el archivo (el índice comienza en 1)
    val firstNameColumn = 4           // El índice de la columna de nombre en el archivo
    val lastNameColumn = 5            // El índice de la columna de apellido en el archivo
    val genderColumn = 6              // El índice de la columna de género en el archivo
    val ipAddressColumn = 7           // El índice de la columna de dirección IP en el archivo
    val hasHeaderRow = true           // Si es `true`, se considera que la primera fila contiene los encabezados de la tabla
    val returnUrl = "https://domain.com/called/after/processing/request"

    ZeroBounceSDK.sendFile(
        context,
        file,
        returnUrl, 
        firstNameColumn, 
        lastNameColumn,
        genderColumn, 
        ipAddressColumn, 
        hasHeaderRow,
        { rsp ->
            Log.d("MainActivity", "sendFile rsp: $rsp")
            // Tu implementación
        },
        { error ->
            Log.e("MainActivity", "sendFile error: $error")
            // Tu implementación
        },
    )
    ```

* ####### El método *getFile* permite obtener el archivo de resultados de validación del archivo enviado utilizando el método *sendFile*
    ```kotlin
    val fileId = "<ID_DEL_ARCHIVO>"    // El ID de archivo devuelto al llamar al método sendFile

    ZeroBounceSDK.getFile(
        context, 
        fileId,
        { rsp -> 
            Log.d("MainActivity", "getFile rsp: $rsp")
            // Tu implementación
        },
        { error -> 
            Log.e("MainActivity", "getFile error: $error") 
            // Tu implementación
        }
    )
    ```

* ####### Verificar el estado de un archivo cargado a través del método *sendFile*
    ```kotlin
    val fileId = "<ID_DEL_ARCHIVO>"    // El ID de archivo devuelto al llamar al método sendFile

    ZeroBounceSDK.fileStatus(
        context, 
        fileId,
        { rsp -> 
            Log.d("MainActivity", "fileStatus rsp: $rsp")
            // Tu implementación
        },
        { error -> 
            Log.e("MainActivity", "fileStatus error: $error") 
            // Tu implementación
        }
    )
    ```

* ####### Eliminar el archivo que se envió utilizando el método *sendFile*. El archivo solo se puede eliminar cuando su estado es `Completado`
    ```kotlin
    val fileId = "<ID_DEL_ARCHIVO>"   // El ID de archivo devuelto al llamar al método sendFile

    ZeroBounceSDK.deleteFile(
        context,
        fileId,
        { rsp -> 
            Log.d("MainActivity", "deleteFile rsp: $rsp")
            // Tu implementación
        },
        { error -> 
            Log.e("MainActivity", "deleteFile error: $error") 
            // Tu implementación
        }
    )
    ```

* ####### Obtener información sobre la actividad de tus suscriptores en cuanto a aperturas, clics, reenvíos y cancelaciones de suscripción que hayan tenido lugar en los últimos 30, 90, 180 o 365 días.
    ```kotlin
    ZeroBounceSDK.getActivityData(
        "<CORREO_A_VALIDAR>",
        { rsp -> 
            Log.d("MainActivity", "validate rsp: $rsp")
            // Tu implementación
        },
        { error -> 
            Log.e("MainActivity", "validate error: $error") 
            //

Tu implementación
}
)
```


##### API de puntuación de IA (AI Scoring)

* ####### El método *scoringSendFile* permite enviar un archivo para la validación masiva de correos electrónicos con puntuación de IA
    ```kotlin
    // Importa java.io.File
    val myFile = File("<RUTA_DEL_ARCHIVO>")  // El archivo en formato CSV o TXT
    val emailAddressColumn = 3        // El índice de la columna de dirección de correo electrónico (el índice comienza en 1)
    val hasHeaderRow = true           // Si es `true`, se considera que la primera fila contiene los encabezados de la tabla
    val returnUrl = "https://domain.com/called/after/processing/request"

    ZeroBounceSDK.scoringSendFile(
        context,
        file,
        emailAddressColumn,
        returnUrl, 
        hasHeaderRow,
        { rsp ->
            Log.d("MainActivity", "scoringSendFile rsp: $rsp")
            // Tu implementación
        },
        { error ->
            Log.e("MainActivity", "scoringSendFile error: $error")
            // Tu implementación
        }
    )
    ```

* ####### El método *scoringGetFile* permite obtener el archivo de resultados de validación del archivo enviado utilizando el método *scoringSendFile*
    ```kotlin
    val fileId = "<ID_DEL_ARCHIVO>"    // El ID de archivo devuelto al llamar al método scoringSendFile

    ZeroBounceSDK.scoringGetFile(
        context, 
        fileId,
        { rsp -> 
            Log.d("MainActivity", "scoringGetFile rsp: $rsp")
            // Tu implementación
        },
        { error -> 
            Log.e("MainActivity", "scoringGetFile error: $error")
            // Tu implementación
        }
    )
    ```

* ####### Verificar el estado de un archivo cargado a través del método *scoringSendFile*
    ```kotlin
    val fileId = "<ID_DEL_ARCHIVO>"    // El ID de archivo devuelto al llamar al método scoringSendFile

    ZeroBounceSDK.scoringFileStatus(
        context, 
        fileId,
        { rsp -> 
            Log.d("MainActivity", "scoringFileStatus rsp: $rsp")
            // Tu implementación
        },
        { error -> 
            Log.e("MainActivity", "scoringFileStatus error: $error") 
            // Tu implementación
        }
    )
    ```

* ####### Eliminar el archivo que se envió utilizando el método *scoringSendFile*. El archivo solo se puede eliminar cuando su estado es `Completado`
    ```kotlin
    val fileId = "<ID_DEL_ARCHIVO>"   // El ID de archivo devuelto al llamar al método scoringSendFile

    ZeroBounceSDK.scoringDeleteFile(
        context, 
        fileId,
        { rsp -> 
            Log.d("MainActivity", "scoringDeleteFile rsp: $rsp")
            // Tu implementación
        },
        { error -> 
            Log.e("MainActivity", "scoringDeleteFile error: $error") 
            // Tu implementación
        }
    )
    ```

##### Documentación
La documentación del SDK se puede generar a través de una tarea de *Gradle*. Abre la pestaña *Gradle* (en la disposición predeterminada, debería estar en el lado derecho de Android Studio), luego ve a *zero_bounce_sdk > Tasks > documentation* y haz doble clic en la tarea ***dokkaHtml

***. Una vez generada, puedes encontrarla en *zero_bounce_sdk/build/dokka/html*. Desde allí, solo tienes que abrir el archivo `index.html`.

##### Publicación
Cada vez que se crea una nueva versión, el flujo de trabajo de CI/CD se ejecutará y se lanzará un nuevo artefacto en Maven Central. No olvides actualizar la versión antes de hacer un lanzamiento. Si alguna vez cambias las credenciales de inicio de sesión de OSSRH, también deberás actualizar las variables del repositorio en Github.

##### Configuración local para lanzamiento manual
Para poder publicar en el repositorio Nexus desde tu máquina local, debes seguir estos pasos:

1. Crea o actualiza el archivo `local.properties` en la raíz del proyecto con la siguiente información:
   ```gradle
   sdk.dir=<TU_DIRECTORIO_DEL_SDK_DE_ANDROID>
   signing.keyId=<LOS_8_ÚLTIMOS_DÍGITOS_DE_TU_CLAVE_GPG>
   signing.password=<TU_CONTRASEÑA_GPG>
   signing.key=<TU_CLAVE_PRIVADA_GPG>  ### las líneas nuevas deben reemplazarse con el carácter de nueva línea '\n'
   ossrhUsername=<TU_NOMBRE_DE_USUARIO_DE_JIRA_DE_SONATYPE>
   ossrhPassword=<TU_CONTRASEÑA_DE_JIRA_DE_SONATYPE>
   sonatypeStagingProfileId=<TU_ID_DE_PERFIL_DE_ETAPA_DE_SONATYPE>
   ```
2. Importa la clave GPG a tu máquina local (ver más abajo)

Si deseas publicar manualmente en el repositorio Nexus (y luego lanzarlo en Maven Central), puedes usar los siguientes comandos:

```shell
### Para publicar en el repositorio de etapas
./gradlew publishReleasePublicationToSonatypeRepository

### Para cerrar y lanzar el artefacto.
./gradlew closeAndReleaseSonatypeStagingRepository
```

Alternativamente, solo puedes ejecutar el primer comando y luego ir a [Nexus Sonatype](https://s01.oss.sonatype.org/), iniciar sesión y abrir *Staging Repositories* y hacer clic en *Refresh*. Aquí verás el artefacto que acabas de cargar. Para publicarlo, debes **cerrarlo** y luego **lanzarlo**. Estas acciones tardarán unos minutos en completarse. Después de **lanzar** el artefacto, tardará:
- algunas horas en aparecer en el [Repositorio de Maven](https://repo1.maven.org/maven2/com/zerobounce/android/zerobouncesdk/) y en la [Búsqueda de Sonatype](https://central.sonatype.com/artifact/com.zerobounce.android/zerobouncesdk/1.1.1)
- de 1 a 3 días en aparecer en el [Repositorio de MVN](https://mvnrepository.com/artifact/com.zerobounce.android/zerobouncesdk)

##### Exportar e importar claves PGP
1. Exporta las claves:
   ```shell
   gpg --list-keys  ### Para obtener el hash de la clave para el siguiente paso
   gpg --export -a <LOS_8_ÚLTIMOS_DÍGITOS> > public.key
   gpg --export-secret-key -a <LOS_8_ÚLTIMOS_DÍGITOS> > private.key
   ```
2. Importa las claves:
   ```shell
   gpg --import public.key
   gpg

--import private.key
   ```
3. Verifica que las nuevas claves estén importadas:
   ```shell
   gpg --list-keys
   gpg --list-secret-keys
   ```
