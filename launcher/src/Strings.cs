using System;
using System.Globalization;
using System.IO;
using System.Text.RegularExpressions;

namespace NoDeal.Launcher
{
    /// <summary>
    /// The launcher's language: Spanish or English, like the game. Each text is
    /// written in both languages where it is used, L.T("Jugar", "Play").
    /// <para>
    /// The language is the one given with --lang; otherwise the one chosen in
    /// the game (libGDX keeps it in %USERPROFILE%\.prefs\NoDeal), so the
    /// launcher, the game and the server agree; otherwise Windows'.
    /// </para>
    /// </summary>
    static class L
    {
        static bool english = Detect();

        /// <summary>Set with --lang: then the server gets it too.</summary>
        public static string Explicit { get; private set; }

        public static bool English
        {
            get { return english; }
        }

        public static string T(string spanish, string englishText)
        {
            return english ? englishText : spanish;
        }

        /// <summary>"es" or "en"; false if it is neither.</summary>
        public static bool Set(string code)
        {
            code = (code ?? "").Trim().ToLowerInvariant();
            if (code != "es" && code != "en")
                return false;
            english = code == "en";
            Explicit = code;
            return true;
        }

        static bool Detect()
        {
            string saved = SavedLanguage();
            if (saved != null)
                return saved == "en";
            return CultureInfo.CurrentUICulture.TwoLetterISOLanguageName != "es";
        }

        /// <summary>The language chosen in the game's options, or null if none was chosen.</summary>
        static string SavedLanguage()
        {
            try
            {
                string prefs = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), @".prefs\NoDeal");
                if (!File.Exists(prefs))
                    return null;
                Match m = Regex.Match(File.ReadAllText(prefs), "<entry key=\"language\">(es|en)</entry>");
                return m.Success ? m.Groups[1].Value : null;
            }
            catch (Exception)
            {
                return null;
            }
        }
    }
}
