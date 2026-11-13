using System;
using System.Text.RegularExpressions;

namespace NoDeal.Launcher
{
    /// <summary>
    /// Turns a crash log into a sentence a player can act on: which thing to
    /// install or update. The first rule that matches the log wins.
    /// </summary>
    class Diagnosis
    {
        public const string VcRedistUrl = "https://aka.ms/vs/17/release/vc_redist.x64.exe";

        public string Heading;
        public string Advice;
        /// <summary>False when trying again can't help (the same bad options).</summary>
        public bool CanRetry = true;

        Diagnosis(string heading, string advice)
        {
            Heading = heading;
            Advice = advice;
        }

        public static Diagnosis For(GameRun run, int exitCode, string log)
        {
            if (Has(log, "UnsupportedClassVersionError"))
                return new Diagnosis(L.T("Tu Java es demasiado viejo", "Your Java is too old"),
                        L.T("No Deal necesita Java " + JavaFinder.RequiredMajor + " o más nuevo y se abrió con Java "
                        + run.Java.Version + ". Instalá Java " + JavaFinder.RequiredMajor + " o superior (por ejemplo "
                        + "Eclipse Temurin desde adoptium.net) y volvé a abrir el launcher.",
                        "No Deal needs Java " + JavaFinder.RequiredMajor + " or newer and it was opened with Java "
                        + run.Java.Version + ". Install Java " + JavaFinder.RequiredMajor + " or newer (for example "
                        + "Eclipse Temurin from adoptium.net) and open the launcher again."));

            if (Has(log, "OutOfMemoryError", "Could not reserve enough space", "Could not create the Java Virtual Machine"))
                return new Diagnosis(L.T("Java no tuvo memoria suficiente", "Java ran out of memory"),
                        L.T("Cerrá otros programas y volvé a intentar.", "Close other programs and try again.")
                        + (run.Java.Arch == "x86" ? L.T(" Tu Java es de 32 bits y no puede usar más de ~1 GB: "
                                + "instalá Java de 64 bits.", " Your Java is 32-bit and can't use more than ~1 GB: "
                                + "install 64-bit Java.") : ""));

            if (Has(log, "UnsatisfiedLinkError", "Couldn't load shared library", "Unable to read file for extraction",
                    "Failed to locate library", "Couldn't extract"))
                return new Diagnosis(L.T("No se pudieron cargar las librerías del juego", "The game's libraries couldn't be loaded"),
                        L.T("El juego extrae unas librerías (.dll) en tu carpeta temporal y no las pudo cargar. Suele ser "
                        + "el antivirus: agregá una excepción para la carpeta de No Deal. Si sigue pasando, instalá "
                        + "Microsoft Visual C++ Redistributable (x64) desde " + VcRedistUrl + ".",
                        "The game extracts some libraries (.dll) to your temporary folder and couldn't load them. It is "
                        + "usually the antivirus: add an exception for the No Deal folder. If it keeps happening, install "
                        + "the Microsoft Visual C++ Redistributable (x64) from " + VcRedistUrl + "."));

            if (Has(log, "GLFW_API_UNAVAILABLE", "GLFW_VERSION_UNAVAILABLE", "GLFW_PLATFORM_ERROR", "WGL:",
                    "Couldn't create window", "Unable to initialize GLFW", "Failed to create OpenGL",
                    "OpenGL 2.0", "No context is current", "glGetString"))
                return new Diagnosis(L.T("No se pudo abrir la ventana del juego (OpenGL)", "The game's window couldn't open (OpenGL)"),
                        L.T("No Deal necesita una placa de video con OpenGL 2.0 o superior. Actualizá los drivers de "
                        + "video desde la página de NVIDIA, AMD o Intel (los que instala Windows a veces no traen "
                        + "OpenGL). En una máquina virtual o por Escritorio remoto suele no funcionar.",
                        "No Deal needs a graphics card with OpenGL 2.0 or newer. Update the graphics drivers "
                        + "from the NVIDIA, AMD or Intel website (the ones Windows installs sometimes lack "
                        + "OpenGL). It usually doesn't work in a virtual machine or over Remote Desktop."));

            if (Has(log, "A fatal error has been detected by the Java Runtime", "EXCEPTION_ACCESS_VIOLATION"))
                return new Diagnosis(run.Name + L.T(" se cerró por un error de bajo nivel", " closed with a low-level error"),
                        L.T("Casi siempre es el driver de video: actualizalo desde la página de NVIDIA, AMD o Intel. "
                        + "En la carpeta de logs quedó un archivo hs_err con el detalle.",
                        "It is almost always the graphics driver: update it from the NVIDIA, AMD or Intel website. "
                        + "The logs folder has an hs_err file with the details."));

            if (Has(log, "Unable to access jarfile", "Invalid or corrupt jarfile", "ZipException",
                    "Error: Could not find or load main class"))
                return new Diagnosis(L.T("Los archivos del juego están incompletos o dañados", "The game's files are incomplete or damaged"),
                        L.T("Volvé a descargar No Deal y descomprimí el zip entero en una carpeta nueva.",
                        "Download No Deal again and extract the whole zip to a new folder."));

            if (Has(log, "java -jar NoDeal-server.jar [--port"))
            {
                Match error = Regex.Match(log, "^Error: (.*)$", RegexOptions.Multiline);
                return new Diagnosis(L.T("Las opciones del servidor no son válidas", "The server options aren't valid"),
                        (error.Success ? error.Groups[1].Value.Trim() + ".\n\n" : "")
                        + L.T("Revisá lo que pusiste después de --server. Abajo está la lista de opciones.",
                        "Check what you wrote after --server. The list of options is below."))
                        { CanRetry = false };
            }

            if (Has(log, "BindException", "Address already in use", "already in use"))
                return new Diagnosis(L.T("El puerto ya está en uso", "The port is already in use"),
                        L.T("Hay otro servidor (u otro programa) usando ese puerto. Cerralo o elegí otro puerto.",
                        "Another server (or another program) is using that port. Close it or choose another port."));

            return new Diagnosis(run.Name + L.T(" se cerró por un error inesperado (código ", " closed with an unexpected error (code ")
                    + exitCode + ")",
                    L.T("Abajo están las últimas líneas del log. Si vuelve a pasar, mandale el log (botón "
                    + "\"Abrir log\") a quien te pasó el juego.",
                    "The last lines of the log are below. If it happens again, send the log (\"Open log\" "
                    + "button) to whoever gave you the game."));
        }

        static bool Has(string log, params string[] needles)
        {
            foreach (string needle in needles)
                if (log.IndexOf(needle, StringComparison.OrdinalIgnoreCase) >= 0)
                    return true;
            return false;
        }

        /// <summary>The last lines of the log without the launcher's header, for the error dialog.</summary>
        public static string Tail(string log, int maxLines)
        {
            string[] lines = Regex.Split(log.TrimEnd(), "\r?\n");
            int start = Math.Max(0, lines.Length - maxLines);
            var text = new System.Text.StringBuilder();
            for (int i = start; i < lines.Length; i++)
                if (!lines[i].StartsWith("# "))
                    text.Append(lines[i]).Append("\r\n");
            string result = text.ToString().Trim();
            return result.Length > 0 ? result : L.T("(el juego no escribió nada antes de cerrarse)",
                    "(the game wrote nothing before closing)");
        }
    }
}
