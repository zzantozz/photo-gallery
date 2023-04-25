package rds.photogallery

class Settings {
    String asString(Setting setting) {
        setting.getValue()
    }

    void setString(Setting setting, String value) {
        setting.value = value
    }

    enum Setting {
        FRAME_STATE_FILE('frame-state.json'),
        PHOTO_DATA_FILE('photo-db.txt'),
        METER_REGISTRY('Graphite'),
        GRAPHITE_HOST('192.168.1.105'),
        PHOTO_ROOT_DIR('')

        String value

        Setting(String value) {
            this.value = value
        }
    }
}
