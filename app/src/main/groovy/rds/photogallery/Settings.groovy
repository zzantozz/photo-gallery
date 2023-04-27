package rds.photogallery

class Settings {
    Settings() {
        for (Setting setting : Setting.values()) {
            def envValue = System.getenv(setting.name())
            if (envValue != null) {
                setting.setValue(envValue)
            }
        }
    }

    String asString(Setting setting) {
        setting.getValue()
    }

    List<String> asStringList(Setting setting) {
        setting.getValue().split("\\s*,\\s*")
    }

    void setString(Setting setting, String value) {
        setting.value = value
    }

    enum Setting {
        FRAME_STATE_FILE('frame-state.json'),
        PHOTO_DATA_FILE('photo-db.txt'),
        METER_REGISTRY('Graphite'),
        GRAPHITE_HOST('192.168.1.105'),
        PHOTO_ROOT_DIR(''),
        EXCLUDED_PATHS(''),
        TAG_FILTER('')

        String value

        Setting(String value) {
            this.value = value
        }
    }
}
