/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.shared.spoof.js.nsigsolver.impl;

import static androidx.javascriptengine.JavaScriptConsoleCallback.ConsoleMessage.LEVEL_DEBUG;
import static androidx.javascriptengine.JavaScriptConsoleCallback.ConsoleMessage.LEVEL_ERROR;
import static androidx.javascriptengine.JavaScriptConsoleCallback.ConsoleMessage.LEVEL_INFO;
import static androidx.javascriptengine.JavaScriptConsoleCallback.ConsoleMessage.LEVEL_LOG;
import static androidx.javascriptengine.JavaScriptConsoleCallback.ConsoleMessage.LEVEL_WARNING;

import androidx.javascriptengine.EvaluationFailedException;
import androidx.javascriptengine.IsolateStartupParameters;
import androidx.javascriptengine.JavaScriptIsolate;
import androidx.javascriptengine.JavaScriptSandbox;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.spoof.js.nsigsolver.common.CacheError;
import app.morphe.extension.shared.spoof.js.nsigsolver.common.ScriptUtils;
import app.morphe.extension.shared.spoof.js.nsigsolver.provider.JsChallengeProviderError;
import app.morphe.extension.shared.spoof.js.nsigsolver.runtime.JsRuntimeChalBaseJCP;
import app.morphe.extension.shared.spoof.js.nsigsolver.runtime.Script;
import app.morphe.extension.shared.spoof.js.nsigsolver.runtime.ScriptSource;
import app.morphe.extension.shared.spoof.js.nsigsolver.runtime.ScriptType;
import app.morphe.extension.shared.spoof.js.nsigsolver.runtime.ScriptVariant;

public class JsEngineChallengeProvider extends JsRuntimeChalBaseJCP {
    private static final JsEngineChallengeProvider INSTANCE = new JsEngineChallengeProvider();

    private final List<String> npmLibFilenames = Arrays.asList(
            LIB_PREFIX + "polyfill.js",
            LIB_PREFIX + "meriyah-6.1.4.min.js",
            LIB_PREFIX + "astring-1.9.0.min.js"
    );

    private JavaScriptSandbox jsSandbox;
    private JavaScriptIsolate jsIsolate;
    private int executeCount = 0;

    private JsEngineChallengeProvider() {}

    public static JsEngineChallengeProvider getInstance() {
        return INSTANCE;
    }

    // Override builtinSource to inject JS specific logic
    @Override
    protected Script builtinSource(ScriptType scriptType) {
        if (scriptType == ScriptType.LIB) {
            Script npmScript = npmSource(scriptType);
            if (npmScript != null) return npmScript;
        }
        return super.builtinSource(scriptType);
    }

    private Script npmSource(ScriptType scriptType) {
        try {
            String code = ScriptUtils.loadScript(npmLibFilenames, "Failed to read js challenge solver lib script");
            return new Script(scriptType, ScriptVariant.V8_NPM, ScriptSource.BUILTIN, SCRIPT_VERSION, code);
        } catch (ScriptUtils.ScriptLoaderError e) {
            Logger.printException(() -> "Failed to read npm source", e);
            return null;
        }
    }

    private void resetIsolate() {
        if (jsIsolate != null) {
            try {
                jsIsolate.close();
            } catch (Exception e) {
                // Ignore close errors.
            }
            jsIsolate = null;
        }
        executeCount = 0;
        Logger.printDebug(() -> "Closed the JavaScript isolate");
    }

    @Override
    protected String runJsRuntime(String stdin) throws JsChallengeProviderError {
        warmup();
        try {
            String result = jsIsolate.evaluateJavaScriptAsync(stdin).get();
            executeCount++;

            if (Utils.isNotEmpty(result)) {
                return result;
            } else {
                var message = "JavaScript engine error: empty response";
                Logger.printException(() -> message);
                throw new JsChallengeProviderError(message);
            }
        } catch (InterruptedException | ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ExecutionException innerExec) {
                cause = innerExec.getCause();
            }
            if (cause instanceof EvaluationFailedException jsError) {
                if (jsError.getMessage() != null && jsError.getMessage().contains("Invalid or unexpected token")) {
                    try {
                        cacheService.clear(CACHE_SECTION);
                    } catch (CacheError ce) {
                        // ignore
                    }
                }
                Logger.printException(() -> "JavaScript engine error", jsError);
                throw new JsChallengeProviderError("JavaScript engine error: " + jsError.getMessage(), jsError);
            }
            Logger.printException(() -> "Execution failed", e);
            throw new JsChallengeProviderError("Execution failed", e);
        }
    }

    public void warmup() {
        try {
            // If the JavaScript sandbox terminates for an unexpected reason, recreate it
            if (jsSandbox == null) {
                Logger.printDebug(() -> "Creating JavaScript sandbox instance");
                jsSandbox = JavaScriptSandbox.createConnectedInstanceAsync(
                        Utils.getContext().getApplicationContext()
                ).get();
            }
            if (jsIsolate == null) {
                // If Android System WebView 110+ (released February 2023) is installed, set the maximum heap memory.
                // This can avoid OOM issues.
                if (jsSandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)) {
                    IsolateStartupParameters params = new IsolateStartupParameters();
                    // Since the size of the Player JS is around 3MB, a maximum heap memory of 128MB is sufficient.
                    params.setMaxHeapSizeBytes(128 * 1024 * 1024); // 128MB
                    Logger.printDebug(() -> "Creating JavaScript isolate instance with max heap size " + params.getMaxHeapSizeBytes() + " bytes");
                    jsIsolate = jsSandbox.createIsolate(params);
                } else { // ~ Android System WebView 109
                    Logger.printDebug(() -> "Creating JavaScript isolate instance (max heap size not supported)");
                    jsIsolate = jsSandbox.createIsolate();
                }
                jsIsolate.addOnTerminatedCallback(terminationInfo -> {
                    Logger.printInfo(() -> String.format(
                            "JavaScript isolate terminated (%s): %s",
                            terminationInfo.getStatusString(),
                            terminationInfo.getMessage()
                    ));
                    executeCount = 0;
                    resetLoadedPlayerState();
                    jsIsolate = null;
                });

                if (jsSandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_CONSOLE_MESSAGING)) {
                    jsIsolate.setConsoleCallback(callback -> {
                        Logger.LogMessage message = () -> "JS Console: " + callback;
                        switch (callback.getLevel()) {
                            case LEVEL_LOG:
                            case LEVEL_DEBUG:
                                Logger.printDebug(message);
                                break;
                            case LEVEL_INFO:
                            case LEVEL_WARNING:
                                Logger.printInfo(message);
                                break;
                            case LEVEL_ERROR:
                                Logger.printException(message);
                                break;
                        }
                    });
                }
            }

            String commonStdin = constructCommonStdin();

            // Declare a global function
            jsIsolate.evaluateJavaScriptAsync(commonStdin).get();
        } catch (Exception e) {
            // ignore warmup errors
            Logger.printInfo(() -> "Error during JavaScript engine warmup, but ignoring", e);
        }
    }
}
