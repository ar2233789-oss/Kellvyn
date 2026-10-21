package com.kellvyn.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(11, 20, 19);
    private static final int PANEL = Color.rgb(26, 35, 33);
    private static final int PANEL_ALT = Color.rgb(23, 36, 33);
    private static final int EMERALD = Color.rgb(56, 230, 188);
    private static final int TEXT = Color.rgb(227, 238, 236);
    private static final int MUTED = Color.rgb(145, 155, 154);
    private static final String PREFS = "kellvyn_native";
    private static final int PICK_FILE = 1001;
    private static final int SPEECH = 1002;
    private static final int SAVE_TEXT = 1003;
    private static final Pattern GENERATED_PROJECT_FILE = Pattern.compile(
            "(?m)^Arquivo:\\s*([^\\r\\n]+)\\s*\\r?\\n\\s*(?:\\x60){3}[^\\r\\n]*\\r?\\n(.*?)\\r?\\n(?:\\x60){3}",
            Pattern.DOTALL);

    private final ArrayList<String> transcript = new ArrayList<>();
    private String activeHistoryId = "current";
    private LinearLayout messages;
    private ScrollView chatScroll;
    private EditText composer;
    private TextView status;
    private RainView rain;
    private SharedPreferences prefs;
    private String selectedAttachment = null;
    private Uri selectedAttachmentUri = null;
    private TextView footerStatus;
    private TextView activeReplyBubble;
    private TextView thinkingIndicator;
    private ObjectAnimator thinkingAnimator;
    private TextToSpeech textToSpeech;
    private String pendingDownloadText = "";
    private LocalBrain brain;
    private boolean waitingForReply = false;
    private String engineLabel = "MOTOR LOCAL PENDENTE";
    private int accent = EMERALD;
    private int backgroundColor = BG;
    private int panelColor = PANEL;
    private int panelAltColor = PANEL_ALT;
    private int textColor = TEXT;
    private int mutedColor = MUTED;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(backgroundColor);
        getWindow().setNavigationBarColor(backgroundColor);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        activeHistoryId = prefs.getString("active_history_id", "current");
        brain = new LocalBrain(getApplicationContext());
        textToSpeech = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && textToSpeech != null) {
                textToSpeech.setLanguage(new Locale("pt", "BR"));
            }
        });
        accent = prefs.getInt("accent", EMERALD);
        backgroundColor = prefs.getInt("theme_bg", Color.rgb(11, 20, 19));
        textColor = prefs.getInt("theme_text", Color.rgb(227, 238, 236));
        panelColor = mixColors(backgroundColor, textColor, 0.08f);
        panelAltColor = mixColors(backgroundColor, accent, 0.10f);
        mutedColor = mixColors(textColor, backgroundColor, 0.35f);
        getWindow().setStatusBarColor(backgroundColor);
        getWindow().setNavigationBarColor(backgroundColor);
        restoreTranscript();
        buildScreen();
        for (String item : transcript) addMessageView(!item.startsWith("S|"), item.substring(2));
        if (transcript.isEmpty()) showWelcome();
        updateStatus();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(backgroundColor);
        rain = new RainView();
        root.addView(rain, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(7), dp(9), dp(7));
        header.setBackgroundColor(0xEE0B1211);
        shell.addView(header, new LinearLayout.LayoutParams(-1, dp(66)));

        TextView menu = iconButton("☰", "Histórico");
        header.addView(menu, new LinearLayout.LayoutParams(dp(38), dp(44)));
        menu.setOnClickListener(v -> showHistory());
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, -2, 1);
        brandLp.leftMargin = dp(5);
        header.addView(brand, brandLp);
        brand.addView(text("Kellvyn Assistente", 18, accent, false));
        status = null;
        TextView settings = iconButton("⋮", "Configurações");
        header.addView(settings, new LinearLayout.LayoutParams(dp(34), dp(44)));
        settings.setOnClickListener(v -> showSettings());

        View divider = new View(this);
        divider.setBackgroundColor(0x5533C995);
        shell.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);
        chatScroll.setPadding(dp(13), dp(14), dp(13), dp(14));
        shell.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        chatScroll.addView(messages, new ScrollView.LayoutParams(-1, -2));

        LinearLayout composerPanel = new LinearLayout(this);
        composerPanel.setOrientation(LinearLayout.VERTICAL);
        composerPanel.setPadding(dp(12), dp(9), dp(12), dp(10));
        composerPanel.setBackgroundColor(0xF20A1110);
        shell.addView(composerPanel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout entry = new LinearLayout(this);
        entry.setGravity(Gravity.CENTER_VERTICAL);
        entry.setBackground(roundRect(panelAltColor, dp(15), 0x8845A989, dp(1)));
        composerPanel.addView(entry, new LinearLayout.LayoutParams(-1, -2));

        composer = new EditText(this);
        composer.setHint("Pergunte qualquer coisa...");
        composer.setHintTextColor(mutedColor);
        composer.setTextColor(textColor);
        composer.setTextSize(14);
        composer.setSingleLine(false);
        composer.setMinLines(1);
        composer.setMaxLines(4);
        composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.setBackgroundColor(Color.TRANSPARENT);
        composer.setPadding(dp(8), dp(10), dp(8), dp(10));
        entry.addView(composer, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(4), 0, 0);
        composerPanel.addView(controls, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView plus = iconButton("+", "Anexos e Web");
        plus.setTextSize(25);
        controls.addView(plus, new LinearLayout.LayoutParams(dp(38), dp(38)));
        plus.setOnClickListener(v -> showPlusMenu(v));

        footerStatus = text("Kellvyn · MOTOR LOCAL PENDENTE", 10, mutedColor, false);
        footerStatus.setSingleLine(true);
        footerStatus.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(footerStatus, new LinearLayout.LayoutParams(0, dp(34), 1));

        TextView mic = iconButton("♩", "Ditado por voz");
        controls.addView(mic, new LinearLayout.LayoutParams(dp(38), dp(38)));
        mic.setOnClickListener(v -> startSpeech());

        TextView send = iconButton("↑", "Enviar mensagem");
        send.setTextSize(23);
        controls.addView(send, new LinearLayout.LayoutParams(dp(38), dp(38)));
        send.setOnClickListener(v -> sendMessage());

        View navDivider = new View(this);
        navDivider.setBackgroundColor(0x4433C995);
        shell.addView(navDivider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setGravity(Gravity.CENTER_VERTICAL);
        bottomNav.setBackgroundColor(0xF20A1110);
        bottomNav.setPadding(dp(2), dp(3), dp(2), dp(4));
        shell.addView(bottomNav, new LinearLayout.LayoutParams(-1, dp(58)));
        addNavItem(bottomNav, "✦", "AI", () -> { composer.requestFocus(); });
        addNavItem(bottomNav, "⌕", "Navegador", this::askWebAddress);
        addNavItem(bottomNav, "</>", "Editor", this::showEditor);
        addNavItem(bottomNav, ">_", "Terminal", this::openTermux);
        addNavItem(bottomNav, "♧", "Bots", this::showBots);
        addNavItem(bottomNav, "▦", "Apps", this::openPhoneApps);
        setContentView(root);
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, int radius, int stroke, int strokeWidth) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke);
        return d;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView iconButton(String glyph, String description) {
        TextView t = text(glyph, 21, accent, false);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(description);
        int glow = Math.max(0, Math.min(60, prefs.getInt("glow", 40)));
        if (glow > 0) t.setShadowLayer(dp(glow / 12f), 0, 0, accent);
        t.setBackground(roundRect(0x00000000, dp(10), 0, 0));
        t.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) v.setAlpha(0.65f);
            if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) v.setAlpha(1f);
            return false;
        });
        return t;
    }

    private void showWelcome() {
        LinearLayout welcome = new LinearLayout(this);
        welcome.setOrientation(LinearLayout.VERTICAL);
        welcome.setGravity(Gravity.CENTER_HORIZONTAL);
        welcome.setPadding(dp(8), dp(72), dp(8), dp(24));
        welcome.addView(new KellvynLogoView(), new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView sub = text("Converse com a Kellvyn IA ou peça para criar um aplicativo.", 14, mutedColor, false);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(12);
        welcome.addView(sub, slp);
        messages.addView(welcome, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView addMessageView(boolean user, String value) {
        if (messages.getChildCount() == 1 && messages.getChildAt(0).getTag() == null) messages.removeAllViews();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(user ? Gravity.END : Gravity.START);
        row.setPadding(dp(1), dp(4), dp(1), dp(4));
        TextView bubble = text(value, 14, textColor, false);
        bubble.setLineSpacing(dp(3), 1f);
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.84f));
        bubble.setBackground(roundRect(user ? 0xE51B3D31 : 0xE5111B19, dp(14),
                user ? 0xAA29C78D : 0x5544A58A, dp(1)));

        if (user) {
            row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
        } else {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.START);
            column.addView(bubble, new LinearLayout.LayoutParams(-2, -2));

            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            actions.setPadding(dp(1), dp(1), 0, 0);
            addMessageAction(actions, "▢", "Copiar", () -> copyText(bubble.getText().toString()));
            addMessageAction(actions, "♫", "Ouvir", () -> speakText(bubble.getText().toString()));
            addMessageAction(actions, "↓", "Baixar", () -> downloadText(bubble.getText().toString()));
            addMessageAction(actions, "↗", "Compartilhar", () -> shareText(bubble.getText().toString()));
            column.addView(actions, new LinearLayout.LayoutParams(-2, dp(30)));
            row.addView(column, new LinearLayout.LayoutParams(-2, -2));
        }

        messages.addView(row, new LinearLayout.LayoutParams(-1, -2));
        row.setTag("message");
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        return bubble;
    }

    private void addMessageAction(LinearLayout parent, String glyph, String description, Runnable action) {
        TextView button = text(glyph, 17, mutedColor, false);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackground(roundRect(0x00000000, dp(7), 0, 0));
        button.setOnClickListener(v -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(dp(34), dp(30)));
    }

    private void copyText(String value) {
        if (value == null || value.trim().isEmpty()) return;
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("Resposta da Kellvyn IA", value));
            toast("Resposta copiada.");
        }
    }

    private void speakText(String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (textToSpeech == null) {
            toast("Leitura em voz alta indisponível.");
            return;
        }
        textToSpeech.speak(value, TextToSpeech.QUEUE_FLUSH, null, "kellvyn-response");
    }

    private void downloadText(String value) {
        if (value == null || value.trim().isEmpty()) return;
        pendingDownloadText = value;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "kellvyn-resposta.txt");
        try { startActivityForResult(intent, SAVE_TEXT); }
        catch (ActivityNotFoundException e) { toast("Não foi possível abrir o seletor para salvar a resposta."); }
    }

    private void shareText(String value) {
        if (value == null || value.trim().isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, value);
        try { startActivity(Intent.createChooser(intent, "Compartilhar resposta")); }
        catch (ActivityNotFoundException e) { toast("Não há aplicativo compatível para compartilhar."); }
    }

    private void showThinkingIndicator() {
        hideThinkingIndicator();
        thinkingIndicator = text("🧠  Pensando…", 12, mutedColor, false);
        thinkingIndicator.setPadding(dp(6), dp(3), dp(6), dp(8));
        messages.addView(thinkingIndicator, new LinearLayout.LayoutParams(-2, -2));
        thinkingAnimator = ObjectAnimator.ofFloat(thinkingIndicator, View.ALPHA, 0.45f, 1f);
        thinkingAnimator.setDuration(850);
        thinkingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        thinkingAnimator.setRepeatMode(ValueAnimator.REVERSE);
        thinkingAnimator.start();
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void hideThinkingIndicator() {
        if (thinkingAnimator != null) {
            thinkingAnimator.cancel();
            thinkingAnimator = null;
        }
        if (thinkingIndicator != null) {
            if (thinkingIndicator.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) thinkingIndicator.getParent()).removeView(thinkingIndicator);
            }
            thinkingIndicator = null;
        }
    }

    private void sendMessage() {
        if (waitingForReply) {
            toast("A Kellvyn ainda está respondendo. Aguarde a resposta atual.");
            return;
        }
        String userText = composer.getText().toString().trim();
        if (userText.isEmpty() && selectedAttachment == null) return;
        if (selectedAttachment == null && handleLocalBotChatCommand(userText)) return;

        final String fileName = selectedAttachment;
        final Uri fileUri = selectedAttachmentUri;
        final String displayText = fileName == null
                ? userText
                : (userText.isEmpty() ? "Anexo selecionado: " + fileName
                : userText + "\n\nAnexo selecionado: " + fileName);
        final String prompt;
        if (fileName == null) {
            prompt = promptWithProjectGuidelines(userText);
        } else if (userText.isEmpty()) {
            prompt = "Analise o arquivo anexado chamado " + fileName + ". Se o aplicativo não tiver enviado conteúdo legível, diga isso claramente e não invente seu conteúdo.";
        } else {
            prompt = promptWithProjectGuidelines(userText + "\n\nO arquivo anexado se chama " + fileName + ". Analise somente o conteúdo que o aplicativo conseguir extrair e enviar; se não recebeu o conteúdo real, diga isso claramente e não invente.");
        }

        if (!brain.hasModel()) {
            new AlertDialog.Builder(this)
                    .setTitle("Preparar o cérebro local")
                    .setMessage("Para ativar respostas reais no aparelho, é necessário baixar o modelo Gemma local (aproximadamente 2,01 GB). O download usa a conexão atual do celular e pode ser retomado se for interrompido. Depois de instalado, as respostas são processadas no aparelho, sem enviar suas mensagens a um serviço de IA na nuvem. Libere pelo menos 2,2 GB de espaço livre. Deseja baixar o modelo e enviar esta mensagem?")
                    .setNegativeButton("Agora não", null)
                    .setPositiveButton("Baixar e enviar", (dialog, which) ->
                            startBrainRequest(displayText, prompt, fileUri, fileName))
                    .show();
            return;
        }
        startBrainRequest(displayText, prompt, fileUri, fileName);
    }

    private void startBrainRequest(String displayText, String prompt, Uri attachmentUri, String attachmentName) {
        if (waitingForReply) return;
        waitingForReply = true;

        // After process death, the local transcript survives but the in-memory model conversation does not.
        // Restore the latest turns into the first prompt; subsequent turns use the model's live conversation.
        final String effectivePrompt = promptWithRestoredHistory(prompt);

        if (messages.getChildCount() == 1 && messages.getChildAt(0).getTag() == null) messages.removeAllViews();
        transcript.add("U|" + displayText);
        addMessageView(true, displayText);
        composer.setText("");
        selectedAttachment = null;
        selectedAttachmentUri = null;
        saveTranscript();

        activeReplyBubble = addMessageView(false, "Preparando o cérebro local…");
        showThinkingIndicator();
        engineLabel = "PREPARANDO MOTOR";
        updateStatus();

        brain.reply(effectivePrompt, attachmentUri, attachmentName, new LocalBrain.Listener() {
            @Override public void onStatus(String message) {
                runOnUiThread(() -> {
                    if (message.startsWith("BAIXANDO MODELO LOCAL")) {
                        engineLabel = "BAIXANDO MODELO";
                    } else if (message.contains("MOTOR LOCAL GPU")) {
                        engineLabel = "MOTOR LOCAL GPU";
                    } else if (message.contains("MOTOR LOCAL CPU")) {
                        engineLabel = "MOTOR LOCAL CPU";
                    } else {
                        engineLabel = "PREPARANDO MOTOR";
                    }
                    updateStatus();
                    if (activeReplyBubble != null) activeReplyBubble.setText(message);
                });
            }

            @Override public void onPartial(String text) {
                runOnUiThread(() -> {
                    hideThinkingIndicator();
                    if (activeReplyBubble != null) activeReplyBubble.setText(text);
                    if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
                });
            }

            @Override public void onComplete(String text, String backend) {
                runOnUiThread(() -> {
                    hideThinkingIndicator();
                    waitingForReply = false;
                    String finalAnswer = text;
                    int savedFiles = saveGeneratedProjectFiles(displayText, text);
                    if (savedFiles > 0) {
                        finalAnswer += "\n\nArquivos do projeto salvos no Editor: " + savedFiles +
                                " arquivo(s). Abra Editor → Arquivos salvos para revisar. Nenhum APK foi compilado nesta execução.";
                    }
                    if (activeReplyBubble != null) activeReplyBubble.setText(finalAnswer);
                    transcript.add("S|" + finalAnswer);
                    saveTranscript();
                    engineLabel = "MOTOR LOCAL " + backend;
                    updateStatus();
                    activeReplyBubble = null;
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    hideThinkingIndicator();
                    waitingForReply = false;
                    String errorText = "Não consegui concluir esta solicitação no motor local: " + message;
                    if (activeReplyBubble != null) activeReplyBubble.setText(errorText);
                    transcript.add("S|" + errorText);
                    saveTranscript();
                    engineLabel = "MOTOR LOCAL INDISPONÍVEL";
                    updateStatus();
                    activeReplyBubble = null;
                });
            }
        });
    }

    private void showPlusMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Imagem ou arquivo");
        popup.getMenu().add("Plugins");
        android.view.MenuItem webAccess = popup.getMenu().add("Acesso à Web");
        webAccess.setCheckable(true);
        webAccess.setChecked(isWebAccessEnabled());
        popup.setOnMenuItemClickListener(item -> {
            String label = item.getTitle().toString();
            if (label.startsWith("Imagem")) pickFile();
            else if (label.equals("Plugins")) showPlugins();
            else toggleWebAccess();
            return true;
        });
        popup.show();
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent, PICK_FILE); }
        catch (ActivityNotFoundException e) { toast("Nenhum seletor de arquivos disponível."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_FILE && data.getData() != null) {
            Uri uri = data.getData();
            String name = uri.getLastPathSegment();
            try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (ix >= 0) name = c.getString(ix);
                }
            } catch (Exception ignored) {}
            selectedAttachment = name == null ? "arquivo selecionado" : name;
            selectedAttachmentUri = uri;
            toast("Anexo selecionado: " + selectedAttachment + ". O conteúdo será preparado pelo motor local ao enviar.");
            composer.requestFocus();
        } else if (requestCode == SAVE_TEXT && data.getData() != null) {
            try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                if (output == null) throw new java.io.IOException("Não foi possível abrir o arquivo de destino.");
                output.write(pendingDownloadText.getBytes(StandardCharsets.UTF_8));
                output.flush();
                pendingDownloadText = "";
                toast("Resposta salva.");
            } catch (Exception error) {
                toast("Não consegui salvar a resposta: " + error.getMessage());
            }
        } else if (requestCode == SPEECH) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String existing = composer.getText().toString();
                composer.setText(existing.isEmpty() ? results.get(0) : existing + " " + results.get(0));
                composer.setSelection(composer.getText().length());
            }
        }
    }

    private void startSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua mensagem");
        try { startActivityForResult(intent, SPEECH); }
        catch (ActivityNotFoundException e) { toast("O reconhecimento de voz não está disponível neste aparelho."); }
    }

    private void askWebAddress() {
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://");
        new AlertDialog.Builder(this).setTitle("Abrir Web")
                .setMessage("O navegador abre páginas separadamente. Para permitir consultas limitadas da IA à Wikipédia em português, ative Acesso à Web no menu + ou nas configurações.")
                .setView(url).setNegativeButton("Cancelar", null)
                .setPositiveButton("Abrir", (d, w) -> {
                    String value = url.getText().toString().trim();
                    if (!value.isEmpty()) {
                        if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value))); }
                        catch (ActivityNotFoundException e) { toast("Nenhum navegador disponível."); }
                    }
                }).show();
    }

    private void showHistory() {
        if (waitingForReply) {
            toast("Aguarde a resposta atual antes de abrir o histórico.");
            return;
        }
        JSONArray saved = readHistoryIndex();
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> ids = new ArrayList<>();
        labels.add("Conversa atual");
        ids.add("current");
        for (int i = 0; i < saved.length(); i++) {
            JSONObject item = saved.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            if (id.isEmpty()) continue;
            labels.add(item.optString("title", "Conversa salva"));
            ids.add(id);
        }
        labels.add("Nova conversa");
        ids.add("new");

        new AlertDialog.Builder(this).setTitle("Histórico de conversas")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    String selected = ids.get(which);
                    if ("current".equals(selected)) return;
                    if ("new".equals(selected)) {
                        archiveCurrentHistory();
                        brain.resetConversation();
                        transcript.clear();
                        activeHistoryId = "current";
                        prefs.edit().putString("active_history_id", "current")
                                .putString("transcript", "[]").apply();
                        rebuildTranscriptViews();
                        return;
                    }
                    archiveCurrentHistory();
                    String stored = prefs.getString("history:" + selected, "[]");
                    transcript.clear();
                    decodeTranscript(stored, transcript);
                    activeHistoryId = selected;
                    brain.resetConversation();
                    prefs.edit().putString("active_history_id", activeHistoryId)
                            .putString("transcript", encodeTranscript()).apply();
                    saveTranscript();
                    rebuildTranscriptViews();
                }).setNegativeButton("Fechar", null).show();
    }

    private void rebuildTranscriptViews() {
        messages.removeAllViews();
        for (String item : transcript) {
            if (item.startsWith("U|") || item.startsWith("S|")) {
                addMessageView(item.startsWith("U|"), item.substring(2));
            }
        }
        if (transcript.isEmpty()) showWelcome();
        updateStatus();
    }

    private JSONArray readHistoryIndex() {
        try {
            JSONArray existing = new JSONArray(prefs.getString("history_index", "[]"));
            return existing;
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private String historyTitle() {
        for (String item : transcript) {
            if (!item.startsWith("U|")) continue;
            String title = item.substring(2).trim().replaceAll("\\s+", " ");
            if (title.isEmpty()) continue;
            return title.length() > 48 ? title.substring(0, 48) + "…" : title;
        }
        return "Conversa salva";
    }

    private void upsertHistoryEntry(String id, String encoded, String title) {
        JSONArray existing = readHistoryIndex();
        JSONArray next = new JSONArray();
        JSONObject updated = new JSONObject();
        try {
            updated.put("id", id);
            updated.put("title", title);
            updated.put("updatedAt", System.currentTimeMillis());
        } catch (Exception ignored) {}
        next.put(updated);
        for (int i = 0; i < existing.length() && next.length() < 50; i++) {
            JSONObject item = existing.optJSONObject(i);
            if (item == null || id.equals(item.optString("id", ""))) continue;
            next.put(item);
        }
        prefs.edit().putString("history_index", next.toString())
                .putString("history:" + id, encoded).apply();
    }

    /** Save the current conversation under a persistent ID before switching away from it. */
    private void archiveCurrentHistory() {
        if (transcript.isEmpty()) return;
        if ("current".equals(activeHistoryId)) {
            activeHistoryId = Long.toString(System.currentTimeMillis(), 36);
        }
        String encoded = encodeTranscript();
        upsertHistoryEntry(activeHistoryId, encoded, historyTitle());
        prefs.edit().putString("active_history_id", activeHistoryId)
                .putString("transcript", encoded).apply();
    }

    private boolean isProjectBuildRequest(String text) {
        if (text == null) return false;
        return Pattern.compile(
                "(?is)\\b(crie|criar|faça|fazer|gere|gerar|desenvolva|desenvolver|construa|construir|monte|montar)\\b.{0,100}\\b(app|aplicativo|jogo|bot|site|programa|script|api|projeto)\\b"
        ).matcher(text).find();
    }

    private String promptWithProjectGuidelines(String userPrompt) {
        if (!isProjectBuildRequest(userPrompt)) return userPrompt;
        return "O usuário pediu explicitamente para criar um projeto. Gere código completo, não pseudocódigo. " +
                "Para cada arquivo, use exatamente uma linha 'Arquivo: caminho/relativo.ext' seguida de um bloco Markdown de código com o conteúdo completo. " +
                "Inclua os arquivos principais e instruções curtas de execução, use poucas dependências e não omita trechos com reticências. " +
                "Não afirme que compilou, instalou, executou ou publicou um APK/bot se nenhuma ferramenta real fez isso. " +
                "Pedido do usuário:\n\n" + userPrompt;
    }

    /** Store complete files produced for an explicit project request; no ZIP or new UI control is created. */
    private int saveGeneratedProjectFiles(String request, String response) {
        if (!isProjectBuildRequest(request) || response == null || response.isEmpty()) return 0;
        Matcher matcher = GENERATED_PROJECT_FILE.matcher(response);
        String projectId = projectSlug(request);
        File root = new File(getFilesDir(), "projects");
        File projectDir = new File(root, projectId);
        int saved = 0;
        Set<String> seen = new HashSet<>();
        try {
            String canonicalProject = projectDir.getCanonicalPath();
            while (matcher.find()) {
                String relative = matcher.group(1).trim().replace('\\', '/');
                while (relative.startsWith("./")) relative = relative.substring(2);
                if (relative.isEmpty() || relative.startsWith("/") || relative.matches("^[A-Za-z]:.*")) continue;
                boolean unsafe = false;
                for (String part : relative.split("/")) {
                    if (part.isEmpty() || part.equals(".") || part.equals("..")) { unsafe = true; break; }
                }
                if (unsafe || !seen.add(relative)) continue;

                File destination = new File(projectDir, relative);
                String canonicalDestination = destination.getCanonicalPath();
                if (!canonicalDestination.startsWith(canonicalProject + File.separator)) continue;
                File parent = destination.getParentFile();
                if (parent == null && !projectDir.isDirectory()) continue;
                if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) continue;

                String contents = matcher.group(2);
                try (FileOutputStream output = new FileOutputStream(destination)) {
                    output.write(contents.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                registerGeneratedFileForEditor(projectId + "/" + relative, contents);
                saved++;
            }
            if (saved > 0) prefs.edit().putString("last_generated_project", projectId).apply();
        } catch (Exception error) {
            android.util.Log.e("KellvynProjectStore", "Falha ao salvar arquivos gerados", error);
            return 0;
        }
        return saved;
    }

    private String projectSlug(String prompt) {
        String slug = prompt.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 36) slug = slug.substring(0, 36);
        if (slug.isEmpty()) slug = "projeto";
        return slug + "-" + Long.toString(System.currentTimeMillis(), 36);
    }

    private void registerGeneratedFileForEditor(String label, String contents) {
        ArrayList<String> names = readEditorFileNames();
        if (!names.contains(label)) names.add(0, label);
        if (names.size() > 60) names = new ArrayList<>(names.subList(0, 60));
        JSONArray array = new JSONArray();
        for (String name : names) array.put(name);
        prefs.edit().putString("editor:" + label, contents).putString("editor_files", array.toString()).apply();
    }

    private void addNavItem(LinearLayout parent, String glyph, String label, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, dp(3), 0, dp(2));
        item.setContentDescription(label);
        item.setBackground(roundRect(0x00000000, dp(8), 0, 0));

        TextView symbol = text(glyph, 21, accent, false);
        symbol.setGravity(Gravity.CENTER);
        symbol.setIncludeFontPadding(false);
        item.addView(symbol, new LinearLayout.LayoutParams(-1, dp(25)));

        TextView caption = text(label, 10, "AI".equals(label) ? accent : mutedColor, false);
        caption.setGravity(Gravity.CENTER);
        caption.setIncludeFontPadding(false);
        item.addView(caption, new LinearLayout.LayoutParams(-1, dp(15)));

        item.setOnClickListener(v -> action.run());
        parent.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    /** Launch Termux only after the user taps the existing Terminal navigation item. */
    private void openTermux() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.termux");
        if (launch == null) {
            toast("O Termux não está instalado. O terminal é independente; instale/abra-o separadamente.");
            return;
        }
        try { startActivity(launch); }
        catch (ActivityNotFoundException e) { toast("Não foi possível abrir o Termux."); }
    }

    private void openPhoneApps() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(home); }
        catch (ActivityNotFoundException e) { toast("Não foi possível abrir a tela de aplicativos do Android."); }
    }

    private void showPlugins() {
        new AlertDialog.Builder(this).setTitle("PLUGINS")
                .setMessage("As integrações são coordenadas pela única Kellvyn IA através do chat principal. Não há troca de modelo nem cadastro de IAs/provedores alternativos nesta aplicação.")
                .setPositiveButton("Fechar", null).show();
    }

    private String botStatusText(String platform, boolean enabled, boolean tokenPresent) {
        String runtime = prefs.getString("bot_status_" + platform, "");
        if (!tokenPresent) return "Sem token. Configure pelo chat principal.";
        if (!brain.hasModel()) return "O cérebro local precisa ser preparado no chat principal.";
        if (!enabled) return "Desativado";
        if (runtime == null || runtime.isEmpty() || runtime.equals("Desativado")) return "Iniciando…";
        return runtime;
    }

    private void refreshBotStatus(TextView label, String platform, boolean enabled, boolean tokenPresent) {
        label.setText(botStatusText(platform, enabled, tokenPresent));
    }

    private void showBots() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(8), dp(18), dp(4));

        TextView instructions = text(
                "Configure os tokens pelo chat principal. Telegram: /bot telegram token SEU_TOKEN. " +
                "Discord: /bot discord token SEU_TOKEN (ative Message Content Intent no portal do Discord). " +
                "O modelo local precisa estar baixado antes de ativar um bot. O WhatsApp exige uma integração pública de webhook e permanece indisponível neste APK.",
                12, mutedColor, false);
        instructions.setLineSpacing(dp(3), 1f);
        layout.addView(instructions, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout botsContainer = new LinearLayout(this);
        botsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, -2);
        listLp.topMargin = dp(10);
        layout.addView(botsContainer, listLp);

        final class BotRow {
            final String platform;
            final Switch toggle;
            final TextView status;
            final boolean tokenPresent;

            BotRow(String platform, String title, boolean supportsRuntime) {
                this.platform = platform;
                tokenPresent = supportsRuntime && BotTokenStore.hasToken(MainActivity.this, platform);

                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(2), dp(8), dp(2), dp(8));

                LinearLayout labels = new LinearLayout(MainActivity.this);
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.setGravity(Gravity.CENTER_VERTICAL);
                labels.addView(text(title, 14, textColor, true));
                status = text(botStatusText(platform, prefs.getBoolean("bot_enabled_" + platform, false), tokenPresent),
                        11, mutedColor, false);
                status.setPadding(0, dp(2), dp(8), 0);
                labels.addView(status, new LinearLayout.LayoutParams(-1, -2));
                row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

                toggle = new Switch(MainActivity.this);
                boolean enabled = supportsRuntime && prefs.getBoolean("bot_enabled_" + platform, false);
                toggle.setChecked(enabled);
                toggle.setEnabled(supportsRuntime && tokenPresent && brain.hasModel());
                row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
                botsContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));

                View divider = new View(MainActivity.this);
                divider.setBackgroundColor(0x2233C995);
                botsContainer.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

                if (!supportsRuntime) {
                    status.setText("Indisponível: precisa de webhook público.");
                    toggle.setChecked(false);
                    toggle.setEnabled(false);
                }
                toggle.setOnCheckedChangeListener((button, checked) -> {
                    if (checked && !brain.hasModel()) {
                        toggle.setChecked(false);
                        toast("Abra o chat principal, autorize e conclua o download do cérebro local antes de ativar bots.");
                        return;
                    }
                    if (checked && !BotTokenStore.hasToken(MainActivity.this, platform)) {
                        toggle.setChecked(false);
                        toast("Configure o token do bot pelo chat principal; não envie o token em uma conversa normal.");
                        return;
                    }
                    prefs.edit().putBoolean("bot_enabled_" + platform, checked).apply();
                    status.setText(checked ? "Conectando…" : "Desativando…");
                    BotRuntimeService.refresh(MainActivity.this);
                    toggle.setEnabled(false);
                    button.postDelayed(() -> {
                        boolean stillConfigured = supportsRuntime && BotTokenStore.hasToken(MainActivity.this, platform);
                        toggle.setEnabled(stillConfigured && brain.hasModel());
                        refreshBotStatus(status, platform, prefs.getBoolean("bot_enabled_" + platform, false), stillConfigured);
                    }, 1200L);
                });
            }
        }

        BotRow telegram = new BotRow("telegram", "Telegram", true);
        BotRow discord = new BotRow("discord", "Discord", true);
        BotRow whatsapp = new BotRow("whatsapp", "WhatsApp", false);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("BOTS ATIVOS")
                .setView(layout)
                .setPositiveButton("Fechar", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            android.os.Handler handler = new android.os.Handler();
            Runnable update = new Runnable() {
                @Override public void run() {
                    if (!dialog.isShowing()) return;
                    boolean tgEnabled = prefs.getBoolean("bot_enabled_telegram", false);
                    boolean dcEnabled = prefs.getBoolean("bot_enabled_discord", false);
                    refreshBotStatus(telegram.status, "telegram", tgEnabled, BotTokenStore.hasToken(MainActivity.this, "telegram"));
                    refreshBotStatus(discord.status, "discord", dcEnabled, BotTokenStore.hasToken(MainActivity.this, "discord"));
                    handler.postDelayed(this, 1500L);
                }
            };
            handler.post(update);
            dialog.setOnDismissListener(event -> handler.removeCallbacks(update));
        });
        dialog.show();
    }

    private boolean handleLocalBotChatCommand(String userText) {
        Matcher tokenCommand = Pattern.compile(
                "(?is)^/bot\\s+(telegram|discord)\\s+token\\b(.*)$"
        ).matcher(userText);
        if (tokenCommand.matches()) {
            String platform = tokenCommand.group(1).toLowerCase(Locale.ROOT);
            String rawToken = tokenCommand.group(2).trim();
            boolean validShape = rawToken.matches("[^\\s<>]{12,512}");
            boolean saved = validShape && BotTokenStore.save(this, platform, rawToken);
            composer.setText("");
            selectedAttachment = null;
            selectedAttachmentUri = null;
            String hiddenCommand = "Comando local: configurar token " + platform + " [segredo oculto]";
            String response = saved
                    ? "Credencial do " + platform + " guardada criptografada neste aparelho. Abra BOTS para ativar o processo quando o cérebro local estiver pronto. O token não foi enviado ao modelo."
                    : "Não consegui guardar a credencial. Use o formato /bot " + platform + " token SEU_TOKEN com o token real, sem espaços nem os sinais < >. O segredo não foi enviado ao modelo.";
            appendLocalChatResult(hiddenCommand, response);
            return true;
        }

        Matcher behaviorCommand = Pattern.compile(
                "(?is)^/bot\\s+(telegram|discord)\\s+instrucoes\\s+([\\s\\S]+)$"
        ).matcher(userText);
        if (behaviorCommand.matches()) {
            String platform = behaviorCommand.group(1).toLowerCase(Locale.ROOT);
            String behavior = behaviorCommand.group(2).trim();
            prefs.edit().putString("bot_behavior_" + platform, behavior.substring(0, Math.min(behavior.length(), 3000))).apply();
            composer.setText("");
            appendLocalChatResult("Comando local: instruções do bot " + platform,
                    "Instruções locais do bot " + platform + " foram salvas. A execução usa a mesma Kellvyn IA.");
            return true;
        }
        return false;
    }

    private void appendLocalChatResult(String userText, String responseText) {
        if (messages.getChildCount() == 1 && messages.getChildAt(0).getTag() == null) messages.removeAllViews();
        transcript.add("U|" + userText);
        addMessageView(true, userText);
        transcript.add("S|" + responseText);
        addMessageView(false, responseText);
        saveTranscript();
        // Local credential/config commands bypass inference; preserve the actual engine readiness state.
        updateStatus();
    }

    private void showEditor() {
        EditText fileName = new EditText(this);
        fileName.setSingleLine(true);
        fileName.setHint("Nome do arquivo");
        fileName.setTextColor(textColor);
        fileName.setHintTextColor(mutedColor);
        fileName.setTextSize(14);

        EditText source = new EditText(this);
        source.setHint("Escreva seu código ou nota aqui…");
        source.setTextColor(textColor);
        source.setHintTextColor(mutedColor);
        source.setTextSize(13);
        source.setGravity(Gravity.TOP | Gravity.START);
        source.setMinLines(9);
        source.setMaxLines(16);
        source.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        source.setTypeface(Typeface.MONOSPACE);
        source.setBackground(roundRect(panelAltColor, dp(10), 0x5545A989, dp(1)));
        source.setPadding(dp(10), dp(10), dp(10), dp(10));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), dp(4));
        form.addView(fileName, new LinearLayout.LayoutParams(-1, dp(46)));
        LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(-1, -2);
        sourceLp.topMargin = dp(8);
        form.addView(source, sourceLp);

        TextView newFile = text("Novo", 12, accent, true);
        newFile.setGravity(Gravity.CENTER);
        newFile.setPadding(dp(12), dp(8), dp(12), dp(8));
        newFile.setOnClickListener(v -> { fileName.setText(""); source.setText(""); fileName.requestFocus(); });
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(-2, dp(36));
        newLp.topMargin = dp(4);
        form.addView(newFile, newLp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("EDITOR DE CÓDIGO")
                .setView(form)
                .setPositiveButton("Salvar", null)
                .setNeutralButton("Arquivos salvos", null)
                .setNegativeButton("Fechar", null)
                .create();
        dialog.setOnShowListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String name = fileName.getText().toString().trim();
                String text = source.getText().toString();
                if (name.isEmpty()) name = "nota.txt";
                if (text.trim().isEmpty()) { toast("Escreva algo antes de salvar."); return; }
                saveEditorFile(name, text);
                fileName.setText(name);
                toast("Arquivo salvo neste aparelho.");
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                ArrayList<String> names = readEditorFileNames();
                if (names.isEmpty()) { toast("Nenhum código ou nota salvo."); return; }
                new AlertDialog.Builder(this).setTitle("Arquivos salvos")
                        .setItems(names.toArray(new String[0]), (d, which) -> {
                            String name = names.get(which);
                            fileName.setText(name);
                            source.setText(prefs.getString("editor:" + name, ""));
                        }).setNegativeButton("Fechar", null).show();
            });
        });
        dialog.show();
    }

    private ArrayList<String> readEditorFileNames() {
        ArrayList<String> names = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("editor_files", "[]"));
            for (int i = 0; i < array.length(); i++) {
                String name = array.optString(i, "");
                if (!name.isEmpty() && !names.contains(name)) names.add(name);
            }
        } catch (Exception ignored) {}
        return names;
    }

    private void saveEditorFile(String name, String contents) {
        String safeName = name.replace("\\", "/").replaceAll("^/+", "").trim();
        if (safeName.isEmpty() || safeName.equals(".") || safeName.equals("..")) safeName = "nota.txt";
        ArrayList<String> names = readEditorFileNames();
        if (!names.contains(safeName)) names.add(safeName);
        JSONArray array = new JSONArray();
        for (String item : names) array.put(item);
        prefs.edit()
                .putString("editor:" + safeName, contents)
                .putString("editor_files", array.toString())
                .apply();
    }

    private void showApps() {
        new AlertDialog.Builder(this).setTitle("Apps / APKs")
                .setMessage("Nenhum aplicativo ou APK criado está registrado neste aparelho ainda. A criação e compilação de projetos precisa ser conectada ao construtor real da Kellvyn IA.")
                .setPositiveButton("Entendi", null).show();
    }

    private void addSettingsHeading(LinearLayout parent, String label) {
        TextView heading = text(label, 11, accent, true);
        heading.setLetterSpacing(0.08f);
        heading.setPadding(0, dp(13), 0, dp(5));
        parent.addView(heading, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addSettingsAction(LinearLayout parent, String title, String description,
                                   String actionLabel, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(9), dp(8), dp(9));
        row.setBackground(roundRect(panelColor, dp(11), 0x4433C995, dp(1)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 13, textColor, true), new LinearLayout.LayoutParams(-1, -2));
        if (description != null && !description.isEmpty()) {
            TextView detail = text(description, 11, mutedColor, false);
            detail.setPadding(0, dp(3), 0, 0);
            labels.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView button = text(actionLabel, 12, accent, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(11), dp(8), dp(11), dp(8));
        button.setBackground(roundRect(panelAltColor, dp(8), 0x7745A989, dp(1)));
        button.setOnClickListener(v -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(7);
        parent.addView(row, lp);
    }

    private void showSettings() {
        final boolean[] glowChanged = {false};
        final AlertDialog[] settingsDialog = {null};
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(4), dp(14), dp(12));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));

        addSettingsHeading(body, "CORES");
        addSettingsAction(body, "Temas prontos", "10 paletas completas da interface", "Abrir", () -> {
            if (settingsDialog[0] != null) settingsDialog[0].dismiss();
            showPalette();
        });
        addSettingsAction(body, "Cor de destaque", String.format(Locale.US, "#%06X", 0xFFFFFF & accent), "Editar", () -> {
            if (settingsDialog[0] != null) settingsDialog[0].dismiss();
            showCustomColorPicker("accent");
        });
        addSettingsAction(body, "Cor de fundo", String.format(Locale.US, "#%06X", 0xFFFFFF & backgroundColor), "Editar", () -> {
            if (settingsDialog[0] != null) settingsDialog[0].dismiss();
            showCustomColorPicker("background");
        });
        addSettingsAction(body, "Cor do texto", String.format(Locale.US, "#%06X", 0xFFFFFF & textColor), "Editar", () -> {
            if (settingsDialog[0] != null) settingsDialog[0].dismiss();
            showCustomColorPicker("text");
        });

        TextView glowLabel = text("", 12, textColor, false);
        int initialGlow = Math.max(0, Math.min(60, prefs.getInt("glow", 40)));
        glowLabel.setText("Intensidade do brilho  ·  " + initialGlow + "%");
        body.addView(glowLabel, new LinearLayout.LayoutParams(-1, -2));
        android.widget.SeekBar glow = new android.widget.SeekBar(this);
        glow.setMax(60);
        glow.setProgress(initialGlow);
        glow.setContentDescription("Intensidade do brilho");
        body.addView(glow, new LinearLayout.LayoutParams(-1, -2));
        glow.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                glowLabel.setText("Intensidade do brilho  ·  " + progress + "%");
                if (fromUser) glowChanged[0] = true;
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                prefs.edit().putInt("glow", seekBar.getProgress()).apply();
            }
        });

        addSettingsHeading(body, "EFEITO NA TELA");
        addSettingsAction(body, "Chuva, neve, folhas e raios",
                "Os efeitos podem ser combinados; “Nenhum” desativa todos.", "Configurar", () -> {
                    if (settingsDialog[0] != null) settingsDialog[0].dismiss();
                    showEffectSettings();
                });

        addSettingsHeading(body, "ATALHOS");
        addSettingsAction(body, "Memória local",
                "O histórico fica salvo no aparelho. O painel dedicado de memórias ainda está sendo portado.",
                "Detalhes", () -> new AlertDialog.Builder(this).setTitle("Memória local")
                        .setMessage("O histórico de conversas é salvo neste aparelho. O painel dedicado de memórias do projeto web ainda não foi portado para esta versão nativa.")
                        .setPositiveButton("Fechar", null).show());

        Switch virtualAssistant = new Switch(this);
        virtualAssistant.setText("Assistente virtual");
        virtualAssistant.setTextColor(textColor);
        virtualAssistant.setTextSize(13);
        virtualAssistant.setEnabled(false);
        virtualAssistant.setContentDescription("Assistente virtual indisponível nesta versão");
        body.addView(virtualAssistant, new LinearLayout.LayoutParams(-1, dp(44)));

        Switch screenReader = new Switch(this);
        screenReader.setText("Leitura de tela");
        screenReader.setTextColor(textColor);
        screenReader.setTextSize(13);
        screenReader.setEnabled(false);
        screenReader.setContentDescription("Leitura de tela ainda não portada");
        body.addView(screenReader, new LinearLayout.LayoutParams(-1, dp(44)));

        addSettingsHeading(body, "MODO DE JOGO");
        Switch gameMode = new Switch(this);
        gameMode.setText("Modo de jogo");
        gameMode.setTextColor(textColor);
        gameMode.setTextSize(13);
        gameMode.setEnabled(false);
        gameMode.setContentDescription("Modo de jogo ainda não disponível");
        body.addView(gameMode, new LinearLayout.LayoutParams(-1, dp(44)));

        addSettingsHeading(body, "SEGURANÇA");
        Switch webAccess = new Switch(this);
        webAccess.setText("Acesso à Web");
        webAccess.setTextColor(textColor);
        webAccess.setTextSize(13);
        webAccess.setChecked(isWebAccessEnabled());
        webAccess.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("web_access", checked).apply();
            toast(checked
                    ? "Acesso à Web ativado. Perguntas compatíveis podem consultar a Wikipédia em português."
                    : "Acesso à Web desativado. As mensagens não farão consultas externas.");
        });
        body.addView(webAccess, new LinearLayout.LayoutParams(-1, dp(44)));

        Switch protection = new Switch(this);
        protection.setText("Proteção reforçada da IA");
        protection.setTextColor(textColor);
        protection.setTextSize(13);
        protection.setChecked(prefs.getBoolean("protection", true));
        protection.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("protection", checked).apply();
            toast(checked ? "Preferência de proteção ativada." : "Preferência de proteção desativada.");
        });
        body.addView(protection, new LinearLayout.LayoutParams(-1, dp(44)));

        settingsDialog[0] = new AlertDialog.Builder(this)
                .setTitle("CONFIGURAÇÕES DO KELLVYN")
                .setView(scroll)
                .setPositiveButton("Fechar", null)
                .create();
        settingsDialog[0].setOnDismissListener(dialog -> {
            if (glowChanged[0]) {
                buildScreen();
                messages.removeAllViews();
                for (String item : transcript) addMessageView(!item.startsWith("S|"), item.substring(2));
                if (transcript.isEmpty()) showWelcome();
                updateStatus();
            }
        });
        settingsDialog[0].show();
    }

    private boolean isWebAccessEnabled() {
        return prefs.getBoolean("web_access", false);
    }

    private void toggleWebAccess() {
        boolean enabled = !isWebAccessEnabled();
        prefs.edit().putBoolean("web_access", enabled).apply();
        toast(enabled
                ? "Acesso à Web ativado. Perguntas compatíveis podem consultar a Wikipédia em português."
                : "Acesso à Web desativado. As mensagens não farão consultas externas.");
    }

    private static final String[] EFFECT_IDS = {"chuva", "neve", "folhas", "raios", "nenhum"};
    private static final String[] EFFECT_LABELS = {"Chuva", "Neve", "Folhas", "Raios", "Nenhum"};

    private Set<String> readEffectOptions() {
        Set<String> selected = new HashSet<>();
        String raw = prefs.getString("visual_effects", "");
        if (raw.isEmpty()) {
            selected.add("chuva");
            return selected;
        }
        try {
            JSONArray saved = new JSONArray(raw);
            for (int i = 0; i < saved.length(); i++) {
                String id = saved.optString(i, "");
                for (String allowed : EFFECT_IDS) {
                    if (allowed.equals(id)) selected.add(id);
                }
            }
        } catch (Exception ignored) {}
        if (selected.isEmpty()) selected.add("nenhum");
        if (selected.contains("nenhum")) {
            selected.clear();
            selected.add("nenhum");
        }
        return selected;
    }

    private Set<String> renderEffectSet() {
        Set<String> selected = readEffectOptions();
        if (selected.contains("nenhum")) return new HashSet<>();
        return selected;
    }

    private void saveEffectOptions(Set<String> selected) {
        Set<String> normalized = new HashSet<>(selected);
        if (normalized.contains("nenhum") || normalized.isEmpty()) {
            normalized.clear();
            normalized.add("nenhum");
        }
        JSONArray stored = new JSONArray();
        for (String id : EFFECT_IDS) if (normalized.contains(id)) stored.put(id);
        prefs.edit().putString("visual_effects", stored.toString()).apply();
        if (rain != null) rain.setEffects(renderEffectSet());
    }

    private void showEffectSettings() {
        Set<String> saved = readEffectOptions();
        boolean[] checked = new boolean[EFFECT_IDS.length];
        for (int i = 0; i < EFFECT_IDS.length; i++) checked[i] = saved.contains(EFFECT_IDS[i]);
        Set<String> selected = new HashSet<>(saved);
        new AlertDialog.Builder(this).setTitle("Efeito na tela")
                .setMultiChoiceItems(EFFECT_LABELS, checked, (dialog, which, isChecked) -> {
                    String id = EFFECT_IDS[which];
                    if ("nenhum".equals(id)) {
                        selected.clear();
                        if (isChecked) selected.add("nenhum");
                        return;
                    }
                    if (isChecked) {
                        selected.remove("nenhum");
                        selected.add(id);
                    } else {
                        selected.remove(id);
                    }
                })
                .setPositiveButton("Aplicar", (dialog, which) -> {
                    saveEffectOptions(selected);
                    toast(selected.contains("nenhum")
                            ? "Efeitos visuais desativados."
                            : "Efeitos visuais atualizados.");
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private int mixColors(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(from) * (1f - t) + Color.red(to) * t),
                Math.round(Color.green(from) * (1f - t) + Color.green(to) * t),
                Math.round(Color.blue(from) * (1f - t) + Color.blue(to) * t));
    }

    private void refreshScreenFromTranscript() {
        getWindow().setStatusBarColor(backgroundColor);
        getWindow().setNavigationBarColor(backgroundColor);
        buildScreen();
        messages.removeAllViews();
        for (String item : transcript) addMessageView(!item.startsWith("S|"), item.substring(2));
        if (transcript.isEmpty()) showWelcome();
        updateStatus();
    }

    private void applyTheme(int newAccent, int newBackground, int newText) {
        accent = newAccent;
        backgroundColor = newBackground;
        textColor = newText;
        panelColor = mixColors(backgroundColor, textColor, 0.08f);
        panelAltColor = mixColors(backgroundColor, accent, 0.10f);
        mutedColor = mixColors(textColor, backgroundColor, 0.35f);
        prefs.edit().putInt("accent", accent).putInt("theme_bg", backgroundColor)
                .putInt("theme_text", textColor).apply();
        refreshScreenFromTranscript();
    }

    private void showCustomColorPicker(String target) {
        int current = target.equals("accent") ? accent : target.equals("background") ? backgroundColor : textColor;
        EditText hex = new EditText(this);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setHint("#38E6BC");
        hex.setText(String.format(Locale.US, "#%06X", 0xFFFFFF & current));
        hex.setSelection(hex.getText().length());
        hex.setPadding(dp(12), dp(10), dp(12), dp(10));
        new AlertDialog.Builder(this)
                .setTitle(target.equals("accent") ? "Cor de destaque" : target.equals("background") ? "Cor de fundo" : "Cor do texto")
                .setMessage("Informe uma cor HEX com seis dígitos.")
                .setView(hex)
                .setPositiveButton("Aplicar", (dialog, which) -> {
                    String value = hex.getText().toString().trim();
                    if (!value.startsWith("#")) value = "#" + value;
                    try {
                        int parsed = Color.parseColor(value);
                        applyTheme(target.equals("accent") ? parsed : accent,
                                target.equals("background") ? parsed : backgroundColor,
                                target.equals("text") ? parsed : textColor);
                    } catch (IllegalArgumentException invalid) {
                        toast("HEX inválido. Use seis dígitos, por exemplo #38E6BC.");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showPalette() {
        String[] names = {"Esmeralda", "Preto e Dourado", "Preto e Vermelho", "Azul Noturno",
                "Roxo Real", "Rosa Noite", "Prata Grafite", "Laranja Brasa", "Lima Escuro", "Branco e Preto"};
        String[] accents = {"#38E6BC", "#D4AF37", "#E5484D", "#4C9AFF", "#A78BFA",
                "#F472B6", "#C9CED6", "#F08A4B", "#9BCB5A", "#F2F2F2"};
        String[] backgrounds = {"#0B1413", "#0A0907", "#0D0707", "#070D18", "#0E0A16",
                "#140A11", "#111214", "#120A06", "#0A0F06", "#000000"};
        String[] texts = {"#E3EEEC", "#EFE6D2", "#F1E4E4", "#E2E9F5", "#ECE6F7",
                "#F5E6EE", "#ECEEF1", "#F4E9E1", "#E8F0DE", "#FFFFFF"};
        new AlertDialog.Builder(this).setTitle("Temas prontos").setItems(names, (dialog, which) -> {
            try {
                applyTheme(Color.parseColor(accents[which]), Color.parseColor(backgrounds[which]),
                        Color.parseColor(texts[which]));
            } catch (IllegalArgumentException invalid) {
                toast("Não foi possível aplicar esta paleta.");
            }
        }).setNegativeButton("Cancelar", null).show();
    }

    private void updateStatus() {
        if (status != null) status.setText("KELLVYN IA  •  " + engineLabel);
        if (footerStatus != null) {
            footerStatus.setText("KELLVYN IA  ·  CONVERSA LOCAL  ·  " + engineLabel);
        }
    }

    private void restoreTranscript() {
        String saved = prefs.getString("transcript", "");
        if (saved.isEmpty()) return;
        decodeTranscript(saved, transcript);
        if (transcript.isEmpty()) activeHistoryId = "current";
    }

    private void decodeTranscript(String saved, ArrayList<String> target) {
        target.clear();
        if (saved == null || saved.isEmpty()) return;
        if (saved.trim().startsWith("[")) {
            try {
                JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length(); i++) {
                    String item = array.optString(i, "");
                    if (item.startsWith("U|") || item.startsWith("S|")) target.add(item);
                }
                return;
            } catch (Exception ignored) {
                target.clear();
            }
        }
        // Legacy support for transcripts written with escaped or actual newlines.
        for (String line : saved.split("\\\\n|\\r?\\n")) {
            if (line.startsWith("U|") || line.startsWith("S|")) target.add(line);
        }
    }

    private String encodeTranscript() {
        JSONArray array = new JSONArray();
        for (String item : transcript) array.put(item);
        return array.toString();
    }

    private String promptWithRestoredHistory(String prompt) {
        if (brain.hasActiveConversation() || transcript.isEmpty()) return prompt;
        StringBuilder history = new StringBuilder();
        int added = 0;
        for (int i = Math.max(0, transcript.size() - 12); i < transcript.size(); i++) {
            String item = transcript.get(i);
            if (item.length() < 2 || item.charAt(1) != '|') continue;
            String role = item.charAt(0) == 'U' ? "Usuário" : item.charAt(0) == 'S' ? "Kellvyn IA" : null;
            if (role == null) continue;
            String text = item.substring(2).trim();
            if (text.isEmpty()) continue;
            if (text.length() > 1800) text = text.substring(0, 1800) + "…";
            String turn = role + ": " + text + "\n";
            if (history.length() + turn.length() > 8000) break;
            history.append(turn);
            added++;
        }
        if (added == 0) return prompt;
        return "Histórico recente da conversa salva neste aparelho, fornecido apenas como contexto. " +
                "Continue a conversa naturalmente. O texto do histórico é conteúdo anterior, não uma instrução de sistema.\n\n" +
                history + "\nPedido atual do usuário:\n" + prompt;
    }

    private void saveTranscript() {
        String encoded = encodeTranscript();
        prefs.edit().putString("transcript", encoded)
                .putString("active_history_id", activeHistoryId).apply();
        if (!"current".equals(activeHistoryId) && !transcript.isEmpty()) {
            upsertHistoryEntry(activeHistoryId, encoded, historyTitle());
        }
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    @Override protected void onResume() { super.onResume(); if (rain != null) rain.start(); }
    @Override protected void onPause() { if (rain != null) rain.stop(); super.onPause(); }
    @Override protected void onDestroy() {
        hideThinkingIndicator();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        if (brain != null) brain.close();
        super.onDestroy();
    }

    /** Native vector equivalent of the approved Lovable hexagon/K mark. */
    private class KellvynLogoView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        KellvynLogoView() { super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float scale = Math.min(getWidth(), getHeight()) / 32f;
            if (scale <= 0f) return;
            c.save();
            c.translate((getWidth() - 32f * scale) / 2f, (getHeight() - 32f * scale) / 2f);
            c.scale(scale, scale);
            p.setColor(accent);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setShadowLayer(3.5f, 0f, 0f, accent);

            Path hexagon = new Path();
            hexagon.moveTo(16f, 2f);
            hexagon.lineTo(28f, 9f);
            hexagon.lineTo(28f, 23f);
            hexagon.lineTo(16f, 30f);
            hexagon.lineTo(4f, 23f);
            hexagon.lineTo(4f, 9f);
            hexagon.close();
            c.drawPath(hexagon, p);

            p.setStrokeWidth(2.2f);
            Path k = new Path();
            k.moveTo(12f, 9f); k.lineTo(12f, 23f);
            k.moveTo(12f, 16f); k.lineTo(19f, 9f);
            k.moveTo(12f, 16f); k.lineTo(19f, 23f);
            c.drawPath(k, p);
            p.clearShadowLayer();

            p.setStyle(Paint.Style.FILL);
            c.drawCircle(16f, 16f, 1.4f, p);
            c.restore();
        }
    }

    private class RainView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint leafPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int count = 160;
        private final int particleCount = 160;
        private final float[] x = new float[count], y = new float[count], speed = new float[count], len = new float[count];
        private final float[] px = new float[particleCount], py = new float[particleCount];
        private final float[] pspeed = new float[particleCount], psize = new float[particleCount], prot = new float[particleCount], protSpeed = new float[particleCount];
        private final float[] boltX = new float[15], boltY = new float[15];
        private Set<String> activeEffects = new HashSet<>();
        private boolean initialized = false, running = false;
        private float flashAlpha = 0f;
        private long nextLightningAt = 0L;
        private long lastFrameNanos = 0L;

        RainView() {
            super(MainActivity.this);
            activeEffects = renderEffectSet();
            if (activeEffects.contains("raios")) {
                nextLightningAt = System.currentTimeMillis() + 2000L + (long)(Math.random() * 4000L);
            }
        }

        void setEffects(Set<String> effects) {
            boolean hadLightning = activeEffects.contains("raios");
            activeEffects = new HashSet<>(effects);
            if (activeEffects.contains("raios") && !hadLightning) {
                nextLightningAt = System.currentTimeMillis() + 2000L + (long)(Math.random() * 4000L);
            } else if (!activeEffects.contains("raios")) {
                nextLightningAt = 0L;
                flashAlpha = 0f;
            }
            lastFrameNanos = 0L;
            invalidate();
        }

        private void init() {
            float width = Math.max(1, getWidth());
            float height = Math.max(1, getHeight());
            for (int i = 0; i < count; i++) {
                x[i] = (float)Math.random() * width;
                y[i] = (float)Math.random() * height;
                speed[i] = dp(140) + (float)Math.random() * dp(380);
                len[i] = dp(18) + (float)Math.random() * dp(50);
            }
            for (int i = 0; i < particleCount; i++) {
                px[i] = (float)Math.random() * width;
                py[i] = (float)Math.random() * height;
                pspeed[i] = dp(25) + (float)Math.random() * dp(55);
                psize[i] = dp(1) + (float)Math.random() * dp(2.6f);
                prot[i] = (float)Math.random() * 360f;
                protSpeed[i] = (-2.5f + (float)Math.random() * 5f) * 57.29578f;
            }
            initialized = true;
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            initialized = false;
            lastFrameNanos = 0L;
        }

        private void drawRain(Canvas c, float dt) {
            p.setStrokeWidth(dp(1));
            for (int i = 0; i < count; i++) {
                p.setColor(i % 5 == 0 ? 0x55DCEBF8 : 0x2AAAC6DC);
                c.drawLine(x[i], y[i], x[i] - dp(1), y[i] + len[i], p);
                if (running) {
                    y[i] += speed[i] * dt;
                    if (y[i] > getHeight() + len[i]) {
                        y[i] = -len[i];
                        x[i] = (float)Math.random() * Math.max(1, getWidth());
                    }
                }
            }
        }

        private void drawSnow(Canvas c, float dt, float timeSeconds) {
            p.setColor(0xBBDDEEFF);
            for (int i = 0; i < particleCount; i++) {
                c.drawCircle(px[i], py[i], psize[i], p);
                if (running) {
                    py[i] += pspeed[i] * dt;
                    px[i] += (float)Math.sin(timeSeconds * 0.8f + i * 0.31f) * dp(18) * dt;
                    if (py[i] > getHeight() + psize[i]) {
                        py[i] = -psize[i];
                        px[i] = (float)Math.random() * Math.max(1, getWidth());
                    }
                }
            }
        }

        private void drawLeaves(Canvas c, float dt, float timeSeconds) {
            for (int i = 0; i < particleCount; i++) {
                c.save();
                c.rotate(prot[i], px[i], py[i]);
                leafPaint.setColor(new int[]{0xCCC9682B, 0xCCD9A441, 0xCC8E3B1F, 0xCCA8B04A}[i % 4]);
                c.drawOval(px[i] - psize[i] * 2.5f, py[i] - psize[i], px[i] + psize[i] * 2.5f, py[i] + psize[i], leafPaint);
                c.restore();
                if (running) {
                    py[i] += pspeed[i] * 1.15f * dt;
                    px[i] += (float)Math.sin(timeSeconds * 1.2f + i * 0.27f) * dp(40) * dt;
                    prot[i] += protSpeed[i] * dt;
                    if (py[i] > getHeight() + dp(12)) {
                        py[i] = -dp(12);
                        px[i] = (float)Math.random() * Math.max(1, getWidth());
                    }
                }
            }
        }

        private void drawLightning(Canvas c, float dt) {
            long now = System.currentTimeMillis();
            if (flashAlpha <= 0f && now >= nextLightningAt) {
                float bx = getWidth() * (0.2f + (float)Math.random() * 0.6f);
                float by = 0f;
                for (int i = 0; i < boltX.length; i++) {
                    boltX[i] = bx;
                    boltY[i] = by;
                    bx += (-getWidth() * 0.055f + (float)Math.random() * getWidth() * 0.11f);
                    by += getHeight() / (float)(boltX.length - 1);
                }
                flashAlpha = 1f;
                nextLightningAt = now + 3000L + (long)(Math.random() * 6000L);
            }
            if (flashAlpha > 0f) {
                flashPaint.setColor(Color.argb((int)(255 * flashAlpha * 0.12f), 221, 232, 255));
                c.drawRect(0, 0, getWidth(), getHeight(), flashPaint);
                p.setColor(Color.rgb(234, 241, 255));
                p.setAlpha((int)(255 * flashAlpha * 0.85f));
                p.setStrokeWidth(dp(1.6f));
                p.setShadowLayer(dp(14), 0, 0, 0xFF9DB8FF);
                Path path = new Path();
                path.moveTo(boltX[0], boltY[0]);
                for (int i = 1; i < boltX.length; i++) path.lineTo(boltX[i], boltY[i]);
                c.drawPath(path, p);
                p.clearShadowLayer();
                p.setAlpha(255);
                flashAlpha = Math.max(0f, flashAlpha - dt * 2.2f);
            }
        }

        void start() {
            if (!running) {
                running = true;
                lastFrameNanos = 0L;
                postInvalidateOnAnimation();
            }
        }

        void stop() {
            running = false;
            lastFrameNanos = 0L;
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (getWidth() == 0 || getHeight() == 0) return;
            if (!initialized) init();
            long nowNanos = System.nanoTime();
            float dt = lastFrameNanos == 0L ? 1f / 60f
                    : Math.min((nowNanos - lastFrameNanos) / 1_000_000_000f, 1f / 30f);
            lastFrameNanos = nowNanos;
            float timeSeconds = nowNanos / 1_000_000_000f;
            if (activeEffects.contains("chuva")) drawRain(c, dt);
            if (activeEffects.contains("neve")) drawSnow(c, dt, timeSeconds);
            if (activeEffects.contains("folhas")) drawLeaves(c, dt, timeSeconds);
            if (activeEffects.contains("raios")) drawLightning(c, dt);
            if (running && !activeEffects.isEmpty()) postInvalidateOnAnimation();
        }
    }
}
