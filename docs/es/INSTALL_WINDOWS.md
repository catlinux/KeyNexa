# Instalar KeyNexa en Windows

[English](../INSTALL_WINDOWS.md) | [Español](INSTALL_WINDOWS.md)

El cliente de Windows es una **versión preliminar**: abre, crea y edita bóvedas KDBX como la app de Android, pero no está firmado y aún no ha pasado la revisión final. Necesita **Windows 10 22H2 o Windows 11, de 64 bits**. No hay que instalar nada más: el runtime de Java va dentro del paquete. Hasta ahora solo se ha probado en Windows 11.

## Descargar y comprobar

Las versiones están en [GitHub Releases](https://github.com/catlinux/KeyNexa/releases). Los archivos de Windows son `KeyNexa-windows-x64-<versión>.msi` (instalador) y `KeyNexa-windows-x64-<versión>.zip` (portable), cada uno con su archivo `.sha256`. Comprueba la descarga antes de ejecutarla:

```powershell
Get-FileHash -Algorithm SHA256 .\KeyNexa-windows-x64-<versión>.msi
```

El hash que muestra debe ser el del archivo `.sha256` que lo acompaña.

Los archivos **no están firmados**, así que Windows SmartScreen muestra «Windows protegió su PC» la primera vez. Comprueba antes el hash y, si coincide, elige *Más información* y *Ejecutar de todas formas*.

## Instalador (MSI)

Haz doble clic en el `.msi` o ejecuta `msiexec /i KeyNexa-windows-x64-<versión>.msi`. Se instala **solo para tu usuario**: sin permisos de administrador ni aviso de UAC. El programa va a `%LOCALAPPDATA%\KeyNexa-app` y aparece un acceso *KeyNexa* en el menú Inicio.

Para actualizar, instala el `.msi` más nuevo encima: sustituye la versión instalada en la carpeta donde esté, también si elegiste otra al instalar. Encuentra esa carpeta también cuando la versión instalada es una anterior.

Para quitarlo, usa *Configuración > Aplicaciones* o `msiexec /x KeyNexa-windows-x64-<versión>.msi`. Tus bóvedas no se tocan nunca, ni tampoco la carpeta de datos que se describe abajo.

## ZIP portable

Extrae el `.zip` donde quieras y ejecuta `KeyNexa\KeyNexa.exe`. No se instala nada ni se crea ningún acceso; borra la carpeta para quitar el programa.

## Dónde guarda KeyNexa sus datos

Las dos formas guardan sus datos privados en `%LOCALAPPDATA%\KeyNexa`: la lista de bóvedas, los ajustes, las copias de seguridad cifradas y el diario de guardado y, si activas las copias en la nube, las credenciales de la nube, selladas con DPAPI de Windows para tu usuario. Los archivos de tus bóvedas se quedan donde los tengas. Desinstalar no borra esta carpeta: bórrala a mano si no quieres dejar rastro.

## Primeros pasos

Elige *Abrir una bóveda existente* (un archivo `.kdbx`) o *Crear una bóveda nueva*. KeyNexa no abre la red hasta que configures las copias en la nube en *Ajustes > Modos*; consulta la [configuración de la nube](CLOUD_SETUP.md) para ver los servicios admitidos.
