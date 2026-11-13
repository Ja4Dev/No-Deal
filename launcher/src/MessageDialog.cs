using System;
using System.Collections.Generic;
using System.Drawing;
using System.Windows.Forms;

namespace NoDeal.Launcher
{
    /// <summary>Colors and fonts shared by the launcher's windows, like the server window's dark theme.</summary>
    static class Theme
    {
        public static readonly Color Background = Color.FromArgb(0x12, 0x14, 0x16);
        public static readonly Color Panel = Color.FromArgb(0x1b, 0x1e, 0x21);
        public static readonly Color Field = Color.FromArgb(0x26, 0x2a, 0x2e);
        public static readonly Color Border = Color.FromArgb(0x33, 0x38, 0x3d);
        public static readonly Color Text = Color.FromArgb(0xd6, 0xd6, 0xd6);
        public static readonly Color Dim = Color.FromArgb(0x96, 0x9c, 0xa2);
        public static readonly Color Red = Color.FromArgb(0xe0, 0x1b, 0x24);
        public static readonly Color RedHover = Color.FromArgb(0xff, 0x3b, 0x3b);
        public static readonly Color Yellow = Color.FromArgb(0xf0, 0xc0, 0x4a);
        public static readonly Color Green = Color.FromArgb(0x5f, 0xd0, 0x6e);

        public static Icon AppIcon()
        {
            try { return Icon.ExtractAssociatedIcon(Application.ExecutablePath); }
            catch (Exception) { return null; }
        }

        public static Button MakeButton(string text, bool primary)
        {
            var button = new Button
            {
                Text = text,
                FlatStyle = FlatStyle.Flat,
                BackColor = primary ? Red : Field,
                ForeColor = Color.White,
                Cursor = Cursors.Hand,
                UseVisualStyleBackColor = false,
                AutoSize = true,
                AutoSizeMode = AutoSizeMode.GrowOnly,
                Padding = new Padding(10, 3, 10, 3),
                Margin = new Padding(8, 0, 0, 0),
            };
            button.FlatAppearance.BorderSize = 1;
            button.FlatAppearance.BorderColor = primary ? Red : Border;
            button.FlatAppearance.MouseOverBackColor = primary ? RedHover : Border;
            button.FlatAppearance.MouseDownBackColor = primary ? Color.FromArgb(0xb0, 0x10, 0x18) : Panel;
            return button;
        }
    }

    /// <summary>
    /// A dark dialog with a heading, an explanation, an optional box with
    /// details (a log) and buttons. Each button runs an action that says
    /// whether the dialog should close, so "Abrir log" can keep it open.
    /// </summary>
    class MessageDialog : Form
    {
        readonly FlowLayoutPanel buttons;
        readonly TextBox details;

        /// <summary>The text of the button that closed the dialog, or null if it was closed with the X.</summary>
        public string Choice { get; private set; }

        public MessageDialog(string title, string heading, string body, string detailText)
        {
            SuspendLayout();
            AutoScaleDimensions = new SizeF(96F, 96F);
            AutoScaleMode = AutoScaleMode.Dpi;
            Text = title;
            Icon = Theme.AppIcon();
            BackColor = Theme.Background;
            ForeColor = Theme.Text;
            Font = new Font("Segoe UI", 9.75F);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            AutoSize = true;
            AutoSizeMode = AutoSizeMode.GrowAndShrink;
            Padding = new Padding(20, 16, 20, 16);

            const int width = 620;
            var layout = new TableLayoutPanel
            {
                ColumnCount = 1,
                AutoSize = true,
                AutoSizeMode = AutoSizeMode.GrowAndShrink,
                Dock = DockStyle.Fill,
            };

            layout.Controls.Add(new Label
            {
                Text = heading,
                AutoSize = true,
                MaximumSize = new Size(width, 0),
                Font = new Font("Segoe UI Semibold", 13F),
                ForeColor = Color.White,
                Margin = new Padding(0, 0, 0, 10),
            });
            layout.Controls.Add(new Label
            {
                Text = body,
                AutoSize = true,
                MaximumSize = new Size(width, 0),
                Margin = new Padding(0, 0, 0, 12),
            });

            if (detailText != null)
            {
                details = new TextBox
                {
                    Text = detailText,
                    Multiline = true,
                    ReadOnly = true,
                    WordWrap = false,
                    ScrollBars = ScrollBars.Both,
                    BackColor = Theme.Panel,
                    ForeColor = Theme.Text,
                    BorderStyle = BorderStyle.FixedSingle,
                    Font = new Font("Consolas", 9F),
                    Size = new Size(width, 220),
                    Margin = new Padding(0, 0, 0, 14),
                };
                layout.Controls.Add(details);
            }

            buttons = new FlowLayoutPanel
            {
                FlowDirection = FlowDirection.RightToLeft,
                AutoSize = true,
                AutoSizeMode = AutoSizeMode.GrowAndShrink,
                Anchor = AnchorStyles.Right,
                Margin = new Padding(0),
                WrapContents = false,
            };
            layout.Controls.Add(buttons);
            // Keeps the dialog at least as wide as the text, even when it is short.
            layout.Controls.Add(new Panel { Size = new Size(width, 0), Margin = new Padding(0) });
            Controls.Add(layout);
            ResumeLayout(false);
            PerformLayout();
        }

        /// <summary>Adds a button, right to left: the first one added ends at the right edge.</summary>
        public MessageDialog AddButton(string text, bool primary, Func<bool> action)
        {
            Button button = Theme.MakeButton(text, primary);
            button.Click += (sender, e) =>
            {
                if (action == null || action())
                {
                    Choice = text;
                    Close();
                }
            };
            buttons.Controls.Add(button);
            if (primary)
                AcceptButton = button;
            return this;
        }

        public string DetailText
        {
            get { return details != null ? details.Text : ""; }
        }

        protected override void OnShown(EventArgs e)
        {
            base.OnShown(e);
            if (details != null)
            {
                // Show the end of the log, where the error is.
                details.SelectionStart = details.TextLength;
                details.ScrollToCaret();
            }
            Activate();
        }

        public string ShowFor(IWin32Window owner)
        {
            if (owner == null)
                ShowDialog();
            else
                ShowDialog(owner);
            return Choice;
        }

        public static void Error(IWin32Window owner, string heading, string body)
        {
            new MessageDialog("No Deal", heading, body, null).AddButton("Cerrar", true, null).ShowFor(owner);
        }
    }
}
