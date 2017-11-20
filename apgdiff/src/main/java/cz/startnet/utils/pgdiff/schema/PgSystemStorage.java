package cz.startnet.utils.pgdiff.schema;

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import ru.taximaxim.codekeeper.apgdiff.ApgdiffUtils;
import ru.taximaxim.codekeeper.apgdiff.Log;

public class PgSystemStorage implements Serializable {

    private static final long serialVersionUID = -5150584184929914163L;

    private static final String FILE_NAME = "system_objects";

    private final List<PgSystemStatement> objects = new ArrayList<>();

    public List<PgSystemStatement> getObjects() {
        return objects;
    }

    public void addObject(PgSystemStatement object) {
        objects.add(object);
    }

    public static PgSystemStorage getObjectsFromResources() {
        try {
            String path = ApgdiffUtils.getFileFromOsgiRes(PgSystemStorage.class.getResource(FILE_NAME)).toString();
            Object object = ApgdiffUtils.deserialize(path);

            if (object != null && object instanceof PgSystemStorage) {
                return (PgSystemStorage) object;
            }
        } catch (URISyntaxException | IOException e) {
            Log.log(Log.LOG_ERROR, "Error while reading systems objects from resources");
        }

        return null;
    }
}