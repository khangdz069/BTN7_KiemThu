package org.example.editvideoytbtool.export;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Locates and invokes FFmpeg without going through a command shell. */
public final class FfmpegService {
    private static final int MAX_DIAGNOSTIC_CHARACTERS = 64_000;

    private final FfmpegConfig config;

    public FfmpegService(FfmpegConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public static FfmpegService systemDefault() {
        return new FfmpegService(FfmpegConfig.systemDefault());
    }

    public FfmpegConfig getConfig() {
        return config;
    }

    public FfmpegProbeResult probe() {
        List<String> command = List.of(config.executable(), "-hide_banner", "-version");
        try {
            ProcessResult result = run(command, config.probeTimeout());
            if (result.exitCode() != 0) {
                return FfmpegProbeResult.unavailable(
                        config.executable(),
                        "Không chạy được FFmpeg (mã lỗi " + result.exitCode() + "). Hãy kiểm tra đường dẫn trong Cài đặt."
                );
            }
            return FfmpegProbeResult.available(config.executable(), firstMeaningfulLine(result.output()));
        } catch (ProcessTimedOutException e) {
            return FfmpegProbeResult.unavailable(
                    config.executable(),
                    "FFmpeg không phản hồi. Hãy kiểm tra lại file thực thi đã cấu hình."
            );
        } catch (IOException e) {
            return FfmpegProbeResult.unavailable(config.executable(), notFoundMessage(config.executable(), e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FfmpegProbeResult.unavailable(config.executable(), "Đã hủy kiểm tra FFmpeg.");
        }
    }

    ProcessResult execute(List<String> arguments) throws ExportException {
        Objects.requireNonNull(arguments, "arguments");
        List<String> command = new ArrayList<>(arguments.size() + 1);
        command.add(config.executable());
        command.addAll(arguments);

        try {
            ProcessResult result = run(command, config.exportTimeout());
            if (result.exitCode() != 0) {
                throw new ExportException(
                        ExportException.Code.ENCODING_FAILED,
                        friendlyEncodingError(result.output(), result.exitCode()),
                        result.output()
                );
            }
            return result;
        } catch (ProcessTimedOutException e) {
            throw new ExportException(
                    ExportException.Code.FFMPEG_TIMED_OUT,
                    "FFmpeg chạy quá thời gian cho phép. File xuất chưa được thay thế.",
                    e.getMessage(),
                    e
            );
        } catch (IOException e) {
            throw new ExportException(
                    ExportException.Code.FFMPEG_NOT_FOUND,
                    notFoundMessage(config.executable(), e),
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExportException(
                    ExportException.Code.EXPORT_CANCELLED,
                    "Đã hủy xuất video.",
                    e
            );
        }
    }

    private static ProcessResult run(List<String> command, Duration timeout)
            throws IOException, InterruptedException, ProcessTimedOutException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(
                () -> drain(process.getInputStream(), output),
                "ffmpeg-output-reader"
        );
        reader.setDaemon(true);
        reader.start();

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            process.destroy();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            throw e;
        }
        if (!finished) {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            reader.join(2_000);
            throw new ProcessTimedOutException("Timed out after " + timeout + ".\n" + snapshot(output));
        }
        reader.join(2_000);
        return new ProcessResult(process.exitValue(), snapshot(output));
    }

    private static void drain(InputStream input, StringBuilder target) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4_096];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                synchronized (target) {
                    if (target.length() < MAX_DIAGNOSTIC_CHARACTERS) {
                        int remaining = MAX_DIAGNOSTIC_CHARACTERS - target.length();
                        target.append(buffer, 0, Math.min(remaining, count));
                    }
                }
            }
        } catch (IOException ignored) {
            // The useful failure is the process exit code; a closed stream during
            // cancellation must not hide it.
        }
    }

    private static String snapshot(StringBuilder output) {
        synchronized (output) {
            return output.toString();
        }
    }

    private static String firstMeaningfulLine(String output) {
        if (output == null) {
            return "";
        }
        return output.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse("");
    }

    private static String notFoundMessage(String executable, IOException cause) {
        String detail = cause.getMessage() == null ? "" : " (" + cause.getMessage() + ")";
        return "Không tìm thấy hoặc không chạy được FFmpeg tại ‘" + executable
                + "’. Hãy cài FFmpeg, thêm vào PATH hoặc chọn ffmpeg.exe trong Cài đặt." + detail;
    }

    private static String friendlyEncodingError(String output, int exitCode) {
        String lower = output == null ? "" : output.toLowerCase(Locale.ROOT);
        if (lower.contains("unknown encoder 'libx264'") || lower.contains("encoder (codec h264) not found")) {
            return "Bản FFmpeg này không có bộ mã hóa H.264 libx264. Hãy cài bản FFmpeg đầy đủ.";
        }
        if (lower.contains("permission denied") || lower.contains("access is denied")) {
            return "FFmpeg không thể ghi file đích. Hãy đóng video đang mở và kiểm tra quyền ghi thư mục.";
        }
        if (lower.contains("no such file") || lower.contains("could not open") || lower.contains("error opening input")) {
            return "FFmpeg không đọc được một tài nguyên âm thanh hoặc hình ảnh của dự án. Hãy kiểm tra các file đã nhập.";
        }
        return "FFmpeg không thể xuất video (mã lỗi " + exitCode + "). Xem chi tiết kỹ thuật để biết thêm.";
    }

    record ProcessResult(int exitCode, String output) {
    }

    private static final class ProcessTimedOutException extends Exception {
        private ProcessTimedOutException(String message) {
            super(message);
        }
    }
}
