package com.kodedu.config;

import com.kodedu.boot.AppStarter;
import com.kodedu.controller.ApplicationController;
import com.kodedu.helper.IOHelper;
import com.kodedu.service.ThreadService;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import jakarta.json.JsonWriter;
import jakarta.json.stream.JsonGenerator;
import javafx.animation.FadeTransition;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Created by usta on 19.07.2015.
 */
public abstract class ConfigurationBase {

    protected final Logger logger = LoggerFactory.getLogger(ConfigurationBase.class);

    protected final ApplicationController controller;
    protected final ThreadService threadService;
    protected Path userHomeConfigPath;

    public abstract String formName();

    @Value("${application.config.folder}")
    private String userHomeConfigFolder;

    @Value("${application.version}")
    private String afxVersion;

    public static ObjectProperty<Path> configRootLocation = new SimpleObjectProperty<>();

    public Path getConfigRootLocation() {
        if (Objects.isNull(configRootLocation.get())) {
            String userHome = System.getProperty("user.home");
            if (Objects.nonNull(userHome)) {
                Path userHomePath = IOHelper.getPath(userHome);
                if (Objects.nonNull(userHomePath)) {
                    userHomeConfigPath = userHomePath.resolve(userHomeConfigFolder);

                    IOHelper.createDirectories(userHomeConfigPath);

                    setConfigRootLocation(userHomeConfigPath);

                    logger.info("Configuration root from User Home System Path {}", userHomeConfigPath.toAbsolutePath());
                    return userHomeConfigPath;
                } else {
                    logger.error("Problem occurred while loading configuration root from User Home System Path not found");
                }
            } else {
                logger.error("Problem occurred while loading configuration root from User Home System Property not found");
            }
        }
        return configRootLocation.get();
    }

    public ObjectProperty<Path> configRootLocationProperty() {
        return configRootLocation;
    }

    public void setConfigRootLocation(Path configRootLocation) {
        this.configRootLocation.set(configRootLocation);
    }

    public ConfigurationBase(ApplicationController controller, ThreadService threadService) {
        this.controller = controller;
        this.threadService = threadService;
    }

    public abstract VBox createForm();

    public Path resolveConfigPath(String fileName) {

        Path configRootLocation = getConfigRootLocation();

        Path configPath = null;

        configPath = configRootLocation.resolve(fileName);

        if (Files.notExists(configPath)) {
            Path configDirectory = getConfigDirectory();
            if (Objects.nonNull(configDirectory)) {
                Path defaultConfigPath = configDirectory.resolve(fileName);
                IOHelper.copy(defaultConfigPath, configPath);
            } else {
                logger.warn("Problem occurred while resolving Config Path for {} Config directory not found", fileName);
            }
        }

        return configPath;
    }

    public Path getConfigDirectory() {
        Path configPath = controller.getConfigPath();
        return configPath;
    }

    public void createConfigFileIfNotExist(Path configPath) {
        try {
            if (!Files.exists(configPath)) {
                Files.writeString(configPath, "{}", UTF_8, CREATE_NEW, WRITE);
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }


    protected void fadeOut(Label label, String text) {
        threadService.runActionLater(() -> {
            label.setText(text);
            FadeTransition fadeTransition = new FadeTransition(Duration.millis(2000), label);
            fadeTransition.setFromValue(1);
            fadeTransition.setToValue(0);
            fadeTransition.playFromStart();
        });
    }

    protected void saveJson(JsonStructure jsonStructure) {

        if (AppStarter.config.isHeadless()) {
            return;
        }

        Map<String, Object> properties = new HashMap<>(1);
        properties.put(JsonGenerator.PRETTY_PRINTING, true);

        try (FileOutputStream fos = new FileOutputStream(getConfigPath().toFile());
             OutputStreamWriter fileWriter = new OutputStreamWriter(fos, "UTF-8");
             JsonWriter jsonWriter = Json.createWriterFactory(properties).createWriter(fileWriter);) {
            jsonWriter.write(jsonStructure);
        } catch (Exception e) {
            logger.error("Problem occurred while saving {}", this.getClass().getSimpleName(), e);
        }
    }

    public abstract Path getConfigPath();

    public void loadPrevious(Path configPath, ActionEvent... actionEvent) {
        load(configPath, actionEvent);
    }

    public abstract void load(Path configPath, ActionEvent... actionEvent);

    public void load(ActionEvent... actionEvent) {
        load(getConfigPath(), actionEvent);
    }

    public abstract void save(ActionEvent... actionEvent);

    /**
     * Creates a JSON object which represents the current configuration of this object.
     * The JSON object is e.g. used when saving the configuration to a file.  
     * @return a JsonObject representing the current state of the configuration
     */
    public abstract JsonObject getJSON();

    public void setOnConfigChanged(Runnable runnable) {
        Field[] fields = this.getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(this);

                if (value instanceof ObservableValue) {
                    ((ObservableValue) value).addListener((observable, oldValue, newValue) -> {
                        if (Objects.nonNull(newValue)) {
                            runnable.run();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void loadPreviousConfiguration(String configDir) {

        Path defaultConfigPath = getConfigPath();
        Path fileName = defaultConfigPath.getFileName();

        String userHome = System.getProperty("user.home");
        Path resolvedConfigPath = Paths.get(userHome).resolve(configDir).resolve(fileName);

        loadPrevious(resolvedConfigPath);
    }
}
