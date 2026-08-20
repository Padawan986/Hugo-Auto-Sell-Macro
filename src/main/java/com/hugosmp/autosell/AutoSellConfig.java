package com.hugosmp.autosell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class AutoSellConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "hugo_autosell.json");

    public int intervalSeconds = 300;
    public String sellCommand = "sell";
    public boolean protectHotbar = false;
    public int clickDelayTicks = 2;

    public static AutoSellConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                AutoSellConfig config = GSON.fromJson(reader, AutoSellConfig.class);
                if (config != null) return config;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        AutoSellConfig config = new AutoSellConfig();
        config.save();
        return config;
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
