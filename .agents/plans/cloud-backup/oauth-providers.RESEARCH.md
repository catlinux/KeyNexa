# Investigación: Dropbox, OneDrive y Google Drive para las copias en la nube

Fecha: 2026-10-15. Hecha por un subagente (Sonnet) con búsqueda web en documentación oficial; es una **hipótesis a verificar**, no una decisión. Lo que no pudo confirmar va marcado **[NO CONFIRMADO]**. Resuelve (en parte) los «puntos abiertos» del [ADR-0008](../../../docs/decisions/0008-cloud-backup.md). Antes de implementar cada servicio hay que comprobar con la API real el redirect en Android y la escritura condicional.

## Recomendación de orden

1. **Dropbox**: app «App folder», PKCE explícito para móvil y código abierto, `rev` para concurrencia, registro gratuito sin verificación de marca. Riesgos: la solicitud de producción a partir de 50 usuarios y comprobar el redirect en Android.
2. **OneDrive**: scope privado adecuado y 412 con eTag. Confirmar antes qué pide el registro y que el PUT con `If-Match` funciona.
3. **Google Drive**: el más costoso (verificación de marca, tokens de 7 días en modo Testing, redirect de Android y firma de F-Droid problemáticos) y la escritura condicional no está confirmada. Al final.

Esto cambia el orden del ADR-0008 (Dropbox, OneDrive, Google Drive): coincide con lo ya decidido.

## Tabla comparativa

| Servicio | Scope | Registro necesario | Verificación / revisión | Problema con la firma de F-Droid | Escritura condicional |
|---|---|---|---|---|---|
| Google Drive | `drive.appdata` (no sensible) | Proyecto Google Cloud + pantalla de consentimiento; cliente Android (paquete + SHA-1) o de escritorio | Sin revisión de scope; verificación de marca (dominio, privacidad) si se publica con nombre o logo; en Testing, 100 usuarios y tokens de 7 días | Sí con cliente Android (otra SHA-1); evitable con cliente de escritorio + loopback (zona gris) | **[NO CONFIRMADO]** |
| Dropbox | App folder (`files.content.*`, sin confirmar) | App en la App Console | Hasta 500 usuarios; a los 50, solicitud de producción | No: `db-<APP_KEY>` no depende de la firma **[NO CONFIRMADO]** | `rev` + `mode: update` (fuentes secundarias) |
| OneDrive | `Files.ReadWrite.AppFolder` | App en Microsoft Entra; posible cuenta o suscripción Azure **[NO CONFIRMADO]** | **[NO CONFIRMADO]** | `msauth://` ata a la firma; hay alternativa sin ella | `If-Match` / eTag con 412 (a verificar en el PUT) |

## Google Drive

- `drive.appdata` está clasificado como **no sensible**, como `drive.file` ([guía](https://developers.google.com/workspace/drive/api/guides/api-specific-auth), 2026-09-03). La carpeta `appDataFolder` está oculta, solo la lee la app que la creó y se borra si el usuario desinstala la app de My Drive ([guía](https://developers.google.com/workspace/drive/api/guides/appdata)). La página no explica cómo identifica Drive «la app».
- La verificación de scopes y la revisión de seguridad se exigen a apps públicas con scopes sensibles o restringidos ([production readiness](https://developers.google.com/identity/protocols/oauth2/production-readiness/overview), 2026-05-22). La **verificación de marca** se exige si la app es externa, está publicada y muestra nombre o logo: pide dominio verificado, página de inicio pública y política de privacidad en ese dominio; exime el «uso personal por ti o unas pocas personas que conoces» ([brand verification](https://developers.google.com/identity/protocols/oauth2/production-readiness/brand-verification), 2026-08-19). **[NO CONFIRMADO]** qué ocurre si se publica sin verificar: solo se sabe que no mostraría nombre ni logo.
- Modo Testing: 100 usuarios de prueba como máximo; con tipo externo y Testing los refresh tokens caducan a los **7 días** (la excepción es solo para los scopes de identidad, no para Drive). Pasar a «In production» lo quita; un refresh token se invalida tras 6 meses sin uso ([OAuth 2.0](https://developers.google.com/identity/protocols/oauth2), [ayuda](https://support.google.com/cloud/answer/15549945)).
- Un cliente Android se vincula al paquete y a la huella SHA-1 del certificado, y cada par es único entre proyectos ([ayuda](https://support.google.com/cloud/answer/15549257)). **Deducción, no documentada:** el APK que F-Droid firma con otra clave necesitaría otro cliente con la huella de F-Droid. **[NO CONFIRMADO]** si dos clientes del mismo proyecto comparten `appDataFolder`.
- Sin Play Services ([apps nativas](https://developers.google.com/identity/protocols/oauth2/native-app), 2026-10-15): los esquemas URI personalizados ya no están soportados en Android; el redirect loopback está marcado obsoleto para clientes Android y se recomienda para clientes de escritorio; PKCE (S256) está soportado y el secreto de cliente es opcional en el intercambio. Una opción es un cliente tipo «aplicación de escritorio» con loopback `http://127.0.0.1:<puerto>` desde Android: zona gris, **[NO CONFIRMADO que esté permitido ni que siga funcionando]**. La vía oficial en Android es la Authorization API de Google Play Services, propietaria (no incompatible con F-Droid solo por eso, pero contraria a «sin SDK propietarios» del ADR).
- Escritura condicional: **[NO CONFIRMADO]** que Drive v3 `files.update` acepte `If-Match`. Alternativa: leer antes y comparar `version` o `md5Checksum` (no atómico).

## Dropbox

- App folder: acceso de lectura y escritura solo a una carpeta con el nombre de la app dentro de `Apps`; se recomienda el permiso mínimo ([guía](https://docs.dropboxapi.com/dropbox-api/docs/developer-resources/developer-guide)). Scopes concretos (`files.content.write` y `read`): **[NO CONFIRMADO]**.
- PKCE sin secreto, pensado para apps de escritorio, móviles y de código abierto ([OAuth](https://docs.dropboxapi.com/dropbox-api/docs/oauth)). Con `token_access_type=offline` se obtiene un refresh token; la aprobación vale hasta que se revoque.
- Redirect en Android: la página oficial solo exige registrar la URI exacta. Fuentes no oficiales indican el esquema `db-<APP_KEY>` en el manifiesto. **[NO CONFIRMADO en documentación oficial.]**
- Registrar apps es gratis. Una app nueva en modo desarrollo admite hasta 500 usuarios; al llegar a 50 usuarios enlazados hay 2 semanas para pedir producción o se congela el enlace de nuevos; exigen guías de marca, un icono y explicar el uso de la API; tras la aprobación el nombre no se puede cambiar. Una sola clave de app para los dos APK evitaría el problema de la firma.
- Escritura condicional: `files/upload` con `mode: update` y `rev` solo se aplica si el `rev` coincide (con `autorename=false` falla; con `true` crea una copia en conflicto). Solo por fuentes secundarias: **[NO CONFIRMADO en la referencia oficial.]**

## OneDrive

- Registro en Microsoft Entra ([guía](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app)): los requisitos dicen «cuenta de Azure con suscripción activa» y un tenant. **[NO CONFIRMADO]** si con cuentas personales basta un tenant gratuito sin suscripción. Hay tipo de cuenta «solo personales» o «cualquier tenant + personales».
- `Files.ReadWrite.AppFolder` da acceso a `/drive/special/approot` ([carpetas especiales](https://learn.microsoft.com/en-us/onedrive/developer/rest-api/concepts/special-folders-appfolder)). **[NO CONFIRMADO]** si exige consentimiento de administrador o su compatibilidad con cuentas personales y de trabajo.
- Redirect ([reply URL](https://learn.microsoft.com/en-us/entra/identity-platform/reply-url)): la plataforma Android registra `msauth://<paquete>/<hash-de-firma>`, lo que ata el cliente a la firma. Alternativa: plataforma «Mobile and desktop» con `msal{client_id}://auth` o loopback (el puerto se ignora al comparar), que permitiría un solo registro para los dos APK. **[NO CONFIRMADO]** que un esquema propio con PKCE funcione sin la librería MSAL.
- Los refresh tokens duran 90 días por defecto y se renuevan con cada uso ([tokens](https://learn.microsoft.com/en-us/entra/identity-platform/refresh-tokens)).
- Escritura condicional: 412 (`resourceModified` / `entityTagDoesNotMatch`) cuando un `If-Match` con eTag no coincide ([errores](https://learn.microsoft.com/en-us/onedrive/developer/rest-api/concepts/errors), [driveItem update](https://learn.microsoft.com/en-us/graph/api/driveitem-update)). **[NO CONFIRMADO en documentación oficial para el PUT de contenido.]**
- No se encontró un proceso de revisión para este scope; **[NO CONFIRMADO]**, no se revisó el programa de «publisher verification».

## Política para un proyecto libre de un autor individual

Ninguna de las páginas leídas ofrece una excepción por ser software libre o sin ánimo de lucro. Google exime de la verificación de marca el «uso personal por ti o unas pocas personas»; Dropbox no cobra y permite 500 usuarios en desarrollo con aprobación a partir de 50; de Microsoft no se leyó nada específico.

## Lo que hay que comprobar antes de implementar cada servicio

1. El redirect en Android con la API real (esquema propio o loopback).
2. La escritura condicional con una subida real y un conflicto provocado.
3. Qué pide el registro (cuenta Azure, dominio y política de privacidad, solicitud de producción).
4. Que un mismo registro sirva a los dos APK (firma de F-Droid).
