package com.hugosmp.autosell;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

public final class HugoAutoSellUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoSellMod.MOD_ID);
    private static final String VERSION_URL = "https://raw.githubusercontent.com/Padawan986/Hugo-Auto-Sell-Macro/refs/heads/main/version.txt";
    private static final String RELEASE_URL = "https://api.github.com/repos/Padawan986/Hugo-Auto-Sell-Macro/releases/latest";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HugoAutoSellUpdater() {}

    public record Update(String version, String downloadUrl) {}

    public static void checkForUpdate(String localVersion, Consumer<Update> onUpdate) {
        CompletableFuture.runAsync(() -> {
            try {
                String remoteVersion = getText(VERSION_URL).trim();
                LOGGER.info("[HugoAutoSell] Update-Check: lokal={}, remote={}", localVersion, remoteVersion);
                if (!isNewer(remoteVersion, localVersion)) {
                    LOGGER.info("[HugoAutoSell] Kein Update verfügbar.");
                    return;
                }

                JsonObject release = JsonParser.parseString(getText(RELEASE_URL)).getAsJsonObject();
                JsonArray assets = release.getAsJsonArray("assets");
                if (assets == null) {
                    LOGGER.warn("[HugoAutoSell] GitHub-Release enthält keine Assets.");
                    return;
                }
                for (var assetElement : assets) {
                    JsonObject asset = assetElement.getAsJsonObject();
                    String name = asset.get("name").getAsString();
                    if (name.endsWith(".jar")) {
                        LOGGER.info("[HugoAutoSell] Update {} gefunden ({}).", remoteVersion, name);
                        onUpdate.accept(new Update(remoteVersion, asset.get("browser_download_url").getAsString()));
                        return;
                    }
                }
                LOGGER.warn("[HugoAutoSell] Kein .jar-Asset im GitHub-Release gefunden.");
            } catch (Exception e) {
                LOGGER.warn("[HugoAutoSell] Update-Check fehlgeschlagen: {}", String.valueOf(e));
            }
        });
    }

    public static void downloadAndInstall(Update update, Consumer<String> onComplete) {
        CompletableFuture.runAsync(() -> {
            try {
                Path installedJar = FabricLoader.getInstance().getModContainer(AutoSellMod.MOD_ID)
                        .orElseThrow(() -> new IOException("Mod-Container nicht gefunden"))
                        .getOrigin().getPaths().stream()
                        .filter(Files::isRegularFile)
                        .findFirst()
                        .orElseThrow(() -> new IOException("Installierte JAR nicht gefunden"));

                Path download = installedJar.resolveSibling(installedJar.getFileName() + ".download");
                HttpRequest request = HttpRequest.newBuilder(URI.create(update.downloadUrl()))
                        .header("User-Agent", "HugoAutoSell-Updater")
                        .timeout(Duration.ofSeconds(30))
                        .GET().build();
                HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(download));
                if (response.statusCode() != 200) throw new IOException("Download fehlgeschlagen (HTTP " + response.statusCode() + ")");
                if (!readJarVersion(download).equals(update.version())) {
                    throw new IOException("Der GitHub-Release enthält nicht die erwartete Version " + update.version());
                }

                try {
                    Files.move(download, installedJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(download, installedJar, StandardCopyOption.REPLACE_EXISTING);
                }
                onComplete.accept(null);
            } catch (Exception exception) {
                onComplete.accept(exception.getMessage() == null ? "Unbekannter Fehler" : exception.getMessage());
            }
        });
    }

    private static String getText(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "HugoAutoSell-Updater")
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
        return response.body();
    }

    private static String readJarVersion(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("fabric.mod.json");
            if (entry == null) throw new IOException("Keine Fabric-Mod-JAR");
            try (var input = zip.getInputStream(entry)) {
                JsonObject metadata = JsonParser.parseReader(new java.io.InputStreamReader(input)).getAsJsonObject();
                if (!AutoSellMod.MOD_ID.equals(metadata.get("id").getAsString())) throw new IOException("Falsche Mod-JAR");
                return metadata.get("version").getAsString();
            }
        }
    }

    private static boolean isNewer(String remote, String local) {
        if (remote == null || local == null || remote.isBlank() || local.isBlank()) return false;
        String[] left = remote.trim().split("\\.");
        String[] right = local.trim().split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int a = i < left.length ? parsePart(left[i]) : 0;
            int b = i < right.length ? parsePart(right[i]) : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    private static int parsePart(String part) {
        String digits = part.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
