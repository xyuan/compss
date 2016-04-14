package integratedtoolkit.components.impl;

import integratedtoolkit.comm.Comm;
import integratedtoolkit.types.data.location.DataLocation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import integratedtoolkit.log.Loggers;
import integratedtoolkit.types.data.*;
import integratedtoolkit.types.data.AccessParams.*;
import integratedtoolkit.types.data.DataAccessId.*;
import integratedtoolkit.types.data.operation.FileTransferable;
import integratedtoolkit.types.data.operation.ObjectTransferable;
import integratedtoolkit.types.data.operation.OneOpWithSemListener;
import integratedtoolkit.types.data.operation.ResultListener;
import integratedtoolkit.types.request.ap.TransferObjectRequest;
import java.io.File;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

public class DataInfoProvider {

    // Constants definition
    private static final String RES_FILE_TRANSFER_ERR = "Error transferring result files";
    //private static final String SERIALIZATION_ERR = "Error serializing object to a file";

    // Map: filename:host:path -> file identifier
    private TreeMap<String, Integer> nameToId;
    // Map: hash code -> object identifier
    private TreeMap<Integer, Integer> codeToId;
    // Map: file identifier -> file information
    private TreeMap<Integer, DataInfo> idToData;
    // Map: Object_Version_Renaming -> Object value
    private TreeMap<String, Object> renamingToValue; 			// TODO: Remove obsolete from here

    private LinkedList<Integer> blockedData;
    // Map: Object_Version_Renaming -> Is_Serialized?
    private HashMap<String, Boolean> renamingToIsSerialized; 	// TODO: Remove obsolete from here

    LinkedList<String> pendingObsoleteRenamings = new LinkedList<String>();

    // Component logger - No need to configure, ProActive does
    private static final Logger logger = Logger.getLogger(Loggers.DIP_COMP);
    private static final boolean debug = logger.isDebugEnabled();

    public DataInfoProvider() {
        nameToId = new TreeMap<String, Integer>();
        codeToId = new TreeMap<Integer, Integer>();
        idToData = new TreeMap<Integer, DataInfo>();
        renamingToValue = new TreeMap<String, Object>();
        renamingToIsSerialized = new HashMap<String, Boolean>();
        blockedData = new LinkedList<Integer>();
        pendingObsoleteRenamings = new LinkedList<String>();
        DataInfo.init();

        logger.info("Initialization finished");
    }

    // DataAccess interface
    public synchronized DataAccessId registerDataAccess(AccessParams access) {
        if (access instanceof FileAccessParams) {
            FileAccessParams fAccess = (FileAccessParams) access;
            return registerFileAccess(fAccess.getMode(),
                    fAccess.getLocation(),
                    -1);
        } else {
            ObjectAccessParams oAccess = (ObjectAccessParams) access;
            return registerObjectAccess(oAccess.getMode(),
                    oAccess.getValue(),
                    oAccess.getCode(),
                    -1);
        }
    }

    public synchronized DataAccessId registerFileAccess(AccessMode mode, DataLocation location, int readerId) {
        DataInfo fileInfo;
        String locationKey = location.getLocationKey();
        Integer fileId = nameToId.get(locationKey);

        // First access to this file
        if (fileId == null) {
            if (debug) {
                logger.debug("FIRST access to " + location.getLocationKey());
            }
            // Update mappings
            fileInfo = new FileInfo(location);
            fileId = fileInfo.getDataId();
            nameToId.put(locationKey, fileId);
            idToData.put(fileId, fileInfo);

            // Register the initial location of the file
            if (mode != AccessMode.W) {
                Comm.registerLocation(fileInfo.getLastDataInstanceId().getRenaming(), location);
            }
        } else {
            // The file has already been accessed, all location are already registered
            if (debug) {
                logger.debug("Another access to " + location.getLocationKey());
            }
            fileInfo = idToData.get(fileId);
        }

        // Version management
        return fileInfo.manageAccess(mode, readerId, debug, logger);
    }

    // Object access
    public synchronized DataAccessId registerObjectAccess(AccessMode mode, Object value, int code, int readerId) {
        DataInfo oInfo;
        Integer aoId = codeToId.get(code);

        // First access to this datum
        if (aoId == null) {
            if (debug) {
                logger.debug("FIRST access to object " + code);
            }

            // Update mappings
            oInfo = new ObjectInfo(code);
            aoId = oInfo.getDataId();
            codeToId.put(code, aoId);
            idToData.put(aoId, oInfo);

            // Serialize this first version of the object to a file
            DataInstanceId lastDID = oInfo.getLastDataInstanceId();
            String renaming = lastDID.getRenaming();

            // Inform the File Transfer Manager about the new file containing the object
            if (mode != AccessMode.W) {
                Comm.registerValue(renaming, value);
            }

        } else {// The datum has already been accessed
            if (debug) {
                logger.debug("Another access to object " + code);
            }

            oInfo = idToData.get(aoId);
        }
        // Version management
        return oInfo.manageAccess(mode, readerId, debug, logger);
    }

    public synchronized boolean alreadyAccessed(DataLocation loc) {
        String locationKey = loc.getLocationKey();
        Integer fileId = nameToId.get(locationKey);
        return fileId != null;
    }

    // DataInformation interface
    public synchronized String getLastRenaming(int code) {
        Integer aoId = codeToId.get(code);
        DataInfo oInfo = idToData.get(aoId);
        return oInfo.getLastDataInstanceId().getRenaming();
    }

    public synchronized DataLocation getOriginalLocation(int fileId) {
        FileInfo info = (FileInfo) idToData.get(fileId);
        return info.getOriginalLocation();
    }

    public synchronized void dataHasBeenRead(List<DataAccessId> dataIds, int readerId) {
        if (!pendingObsoleteRenamings.isEmpty() && blockedData.isEmpty()) {// Flush pending obsolete renamings when there's no blocked data
            for (String renaming : pendingObsoleteRenamings) {
                Comm.removeData(renaming);
            }
            pendingObsoleteRenamings.clear();
        }

        for (DataAccessId dAccId : dataIds) {
            Integer rDataId = null;
            Integer rVersionId = null;
            String rRenaming = null;
            Integer wDataId = null;
            Integer wVersionId = null;
            String wRenaming = null;
            if (dAccId instanceof RAccessId) {
                rDataId = ((RAccessId) dAccId).getReadDataInstance().getDataId();
                rVersionId = ((RAccessId) dAccId).getReadDataInstance().getVersionId();
                rRenaming = ((RAccessId) dAccId).getReadDataInstance().getRenaming();
            } else if (dAccId instanceof RWAccessId) {
                rDataId = ((RWAccessId) dAccId).getReadDataInstance().getDataId();
                rVersionId = ((RWAccessId) dAccId).getReadDataInstance().getVersionId();
                rRenaming = ((RWAccessId) dAccId).getReadDataInstance().getRenaming();
                wDataId = ((RWAccessId) dAccId).getWrittenDataInstance().getDataId();
                wVersionId = ((RWAccessId) dAccId).getWrittenDataInstance().getVersionId();
                wRenaming = ((RWAccessId) dAccId).getWrittenDataInstance().getRenaming();
            } else {
                wDataId = ((WAccessId) dAccId).getWrittenDataInstance().getDataId();
                wVersionId = ((WAccessId) dAccId).getWrittenDataInstance().getVersionId();
                wRenaming = ((WAccessId) dAccId).getWrittenDataInstance().getRenaming();
            }
            if (rDataId != null) {
                DataInfo fileInfo = idToData.get(rDataId);
                if (fileInfo.versionHasBeenRead(rVersionId, readerId) == 0 && (fileInfo.getLastVersionId() != rVersionId || fileInfo.isToDelete())) {
                    if (blockedData.contains(rDataId)) {
                        logger.debug("File " + rRenaming + " is in pending obsolete renamings");
                        pendingObsoleteRenamings.add(rRenaming);
                    } else {
                        logger.debug("Detected file " + rRenaming + " as obsolete");
                        Comm.removeData(rRenaming);
                    }
                }
            }
            if (wDataId != null) {
                DataInfo fileInfo = idToData.get(wDataId);
                if (fileInfo == null) {
                    if (blockedData.contains(wDataId)) {
                        logger.debug("File " + wRenaming + " is in pending obsolete renamings");
                        pendingObsoleteRenamings.add(wRenaming);
                    } else {
                        logger.debug("Detected file " + wRenaming + " as obsolete");
                        Comm.removeData(wRenaming);
                    }
                } else if (fileInfo.isToDelete()) {
                    idToData.remove(wDataId);
                    if (blockedData.contains(wDataId)) {
                        logger.debug("File " + wRenaming + " is in pending obsolete renamings");
                        pendingObsoleteRenamings.add(wRenaming);
                    } else {
                        logger.debug("Detected file " + wRenaming + " as obsolete");
                        Comm.removeData(wRenaming);
                    }
                }
            }
        }
    }

    public synchronized void setObjectVersionValue(String renaming, Object value) {
        renamingToValue.put(renaming, value);
        Comm.registerValue(renaming, value);
    }

    public synchronized boolean isHere(DataInstanceId dId) {
        return renamingToValue.get(dId.getRenaming()) != null;
    }

    public synchronized Object getObject(String renaming) {
        return renamingToValue.get(renaming);
    }

    public synchronized void newVersionSameValue(String rRenaming, String wRenaming) {
        renamingToValue.put(wRenaming, renamingToValue.get(rRenaming));
    }

    public synchronized DataInstanceId getLastDataAccess(int code) {
        Integer aoId = codeToId.get(code);
        DataInfo oInfo = idToData.get(aoId);
        return oInfo.getLastDataInstanceId();
    }

    public synchronized List<DataInstanceId> getLastVersions(TreeSet<Integer> dataIds) {
        List<DataInstanceId> versionIds = new ArrayList<DataInstanceId>(dataIds.size());
        for (Integer dataId : dataIds) {
            DataInfo dataInfo = idToData.get(dataId);
            if (dataInfo != null) {
                versionIds.add(dataInfo.getLastDataInstanceId());
            } else {
                versionIds.add(null);
            }
        }
        return versionIds;
    }

    public synchronized void blockDataIds(TreeSet<Integer> dataIds) {
        blockedData.addAll(dataIds);
    }

    public synchronized void unblockDataId(Integer dataId) {
        blockedData.remove(dataId);
    }

    public synchronized FileInfo deleteData(DataLocation loc) {
        String locationKey = loc.getLocationKey();
        Integer fileId = nameToId.get(locationKey);
        if (fileId == null) {
            return null;
        }

        FileInfo fileInfo = (FileInfo) idToData.get(fileId);
        if (fileInfo.getReaders() == 0) {
            nameToId.remove(locationKey);
            idToData.remove(fileId);
            Comm.removeData(fileInfo.getLastDataInstanceId().getRenaming());
        } else {
            fileInfo.setToDelete(true);
        }
        return fileInfo;
    }

    public synchronized void transferObjectValue(TransferObjectRequest toRequest) {
        Semaphore sem = toRequest.getSemaphore();
        DataAccessId daId = toRequest.getDaId();
        RWAccessId rwaId = (RWAccessId) daId;

        String sourceName = rwaId.getReadDataInstance().getRenaming();
        //String targetName = rwaId.getWrittenDataInstance().getRenaming();

        LogicalData ld = Comm.getData(sourceName);

        if (ld.isInMemory()) {
            if (!ld.isOnFile()) { // Only if there are no readers
                try {
                    ld.writeToFileAndRemoveValue();
                } catch (Exception e) {
                    logger.fatal("Exception writing object to file.", e);
                }
            } else {
            	Comm.clearValue(sourceName);
            }
            toRequest.setResponse(ld.getValue());
            toRequest.getSemaphore().release();
        } else {
            DataLocation targetLocation = DataLocation.getLocation(Comm.appHost, Comm.appHost.getTempDirPath() + sourceName);
            Comm.appHost.getData(sourceName, targetLocation, new ObjectTransferable(), new OneOpWithSemListener(sem));
        }
    }

    public synchronized ResultFile blockDataAndGetResultFile(int dataId, ResultListener listener) {
        DataInstanceId lastVersion;
        FileInfo fileInfo = (FileInfo) idToData.get(dataId);
        if (fileInfo != null) {
            String[] splitPath = fileInfo.getOriginalLocation().getPath().split(File.separator);
            String origName = splitPath[splitPath.length - 1];
            if (origName.startsWith("compss-serialized-obj_")) { // Do not transfer objects serialized by the bindings
                if (debug) {
                    logger.debug("Discarding file " + origName + " as a result");
                }
                return null;
            }
            lastVersion = fileInfo.getLastDataInstanceId();
            blockedData.add(dataId);
            ResultFile rf = new ResultFile(lastVersion, fileInfo.getOriginalLocation());

            DataInstanceId fId = rf.getFileInstanceId();
            String renaming = fId.getRenaming();

            // Look for the last available version
            while (renaming != null && !Comm.existsData(renaming)) {
                renaming = DataInstanceId.previousVersionRenaming(renaming);
            }
            if (renaming == null) {
                logger.error(RES_FILE_TRANSFER_ERR + ": Cannot transfer file " + fId.getRenaming() + " nor any of its previous versions");
                return null;
            }

            listener.addOperation();
            Comm.appHost.getData(renaming, rf.getOriginalLocation(), new FileTransferable(), listener);
            return rf;
        }
        return null;
    }

    public synchronized void shutdown() {
        //Nothing to do
    }

}
