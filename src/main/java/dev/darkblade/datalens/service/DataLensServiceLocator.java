package dev.darkblade.datalens.service;

import dev.darkblade.datalens.adapter.common.Adapter;
import dev.darkblade.datalens.core.diff.DiffService;
import dev.darkblade.datalens.core.edit.EditService;
import dev.darkblade.datalens.core.inspect.InspectorService;
import dev.darkblade.datalens.core.serialize.SerializationService;
import dev.darkblade.datalens.core.export.DiskExportService;
import dev.darkblade.datalens.core.search.TreeSearchService;
import dev.darkblade.datalens.core.session.SessionService;
import dev.darkblade.datalens.core.validate.ValidationService;
import dev.darkblade.datalens.repository.ChangeLogRepository;

import java.io.File;
import java.util.Objects;

/**
 * Central service registry for DataLens.
 * All services are initialised once at plugin startup and accessed from here.
 * No dependency injection framework is used — keep it simple for a single-plugin context.
 */
public final class DataLensServiceLocator {

    private static DataLensServiceLocator instance;

    private final Adapter adapter;
    private final InspectorService inspectorService;
    private final ValidationService validationService;
    private final EditService editService;
    private final SerializationService serializationService;
    private final DiffService diffService;
    private final SessionService sessionService;
    private final ChangeLogRepository changeLogRepository;
    private final TreeSearchService treeSearchService;
    private final DiskExportService diskExportService;

    private DataLensServiceLocator(
            Adapter adapter,
            SessionService sessionService,
            ChangeLogRepository changeLogRepository,
            File dataFolder
    ) {
        this.adapter = adapter;
        this.validationService = new ValidationService();
        this.changeLogRepository = changeLogRepository;
        this.inspectorService = new InspectorService(adapter);
        this.editService = new EditService(adapter, validationService, changeLogRepository);
        this.serializationService = new SerializationService();
        this.diffService = new DiffService();
        this.sessionService = sessionService;
        this.treeSearchService = new TreeSearchService();
        this.diskExportService = new DiskExportService(dataFolder != null ? dataFolder : new File("."), this.serializationService);
    }

    /**
     * Initialises the service locator. Must be called once during {@code onEnable}.
     */
    public static void init(Adapter adapter, SessionService sessionService, ChangeLogRepository changeLog) {
        init(adapter, sessionService, changeLog, null);
    }

    public static void init(Adapter adapter, SessionService sessionService, ChangeLogRepository changeLog, File dataFolder) {
        instance = new DataLensServiceLocator(adapter, sessionService, changeLog, dataFolder);
    }

    public static DataLensServiceLocator get() {
        return Objects.requireNonNull(instance, "DataLensServiceLocator not initialised");
    }

    public Adapter adapter() { return adapter; }
    public InspectorService inspector() { return inspectorService; }
    public ValidationService validator() { return validationService; }
    public EditService editor() { return editService; }
    public SerializationService serializer() { return serializationService; }
    public DiffService differ() { return diffService; }
    public SessionService sessions() { return sessionService; }
    public ChangeLogRepository changeLog() { return changeLogRepository; }
    public TreeSearchService searcher() { return treeSearchService; }
    public DiskExportService exporter() { return diskExportService; }
}
