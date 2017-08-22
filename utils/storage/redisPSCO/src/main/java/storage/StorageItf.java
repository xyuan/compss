package storage;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import storage.utils.Serializer;

public final class StorageItf {

    // Logger According to Loggers.STORAGE
    private static final Logger LOGGER = LogManager.getLogger("es.bsc.compss.Storage");

    // Error Messages
    private static final String ERROR_HOSTNAME = "ERROR_HOSTNAME";

    // Directories
    private static final String BASE_WORKING_DIR = File.separator + "tmp" + File.separator + "PSCO" + File.separator;

    private static final String MASTER_HOSTNAME;
    private static final String MASTER_WORKING_DIR;

    private static final String ID_EXTENSION = ".ID";
    private static final String PSCO_EXTENSION = ".PSCO";

    // Redis variables

    // This port is the official Redis Port
    // The storage API will assume that, given a hostname, there is a Redis Server listening there
    private static final int REDIS_PORT = 6379;
    // Client connection
    private static Jedis redisConnection;

    private static List<String> hosts = new ArrayList<>();


    static {
        String hostname = null;
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            hostname = localHost.getCanonicalHostName();
        } catch (UnknownHostException e) {
            System.err.println(ERROR_HOSTNAME);
            e.printStackTrace();
            System.exit(1);
        }
        MASTER_HOSTNAME = hostname;
        MASTER_WORKING_DIR = BASE_WORKING_DIR + File.separator + MASTER_HOSTNAME + File.separator;
    }

    /**
     * Constructor
     */
    public StorageItf() {
        // Nothing to do since everything is static
    }

    /**
     * Initializes the persistent storage
     * Configuration file must contain all the worker hostnames, one by line
     *
     * @param storageConf Path to the storage configuration File
     * @throws StorageException
     */
    public static void init(String storageConf) throws StorageException, IOException {
        BufferedReader br = new BufferedReader(new FileReader(storageConf));
        String line = null;
        while((line = br.readLine()) != null) {
            hosts.add(line.trim());
        }
        // We should be able to connect to the master node with no problem
        redisConnection = new Jedis(hosts.get(0), 6379);
    }

    /**
     * Stops the persistent storage
     * 
     * @throws StorageException
     */
    public static void finish() throws StorageException {
        redisConnection.close();
    }

    /**
     * Returns all the valid locations of a given id
     * WARNING: Given that Redis has no immediate mechanisms to retrieve this information, we will return
     * all the nodes instead, because a connection to any of them will grant us that we can retrieve it
     * @param id
     * @return
     * @throws StorageException
     */
    public static List<String> getLocations(String id) throws StorageException {
        return hosts;
    }

    /**
     * Creates a new replica of PSCO id @id in host @hostname
     * 
     * @param id
     * @param hostName
     * @throws StorageException
     */
    public static void newReplica(String id, String hostName) throws StorageException {
        throw new StorageException("Redis does not support this feature");
    }

    /**
     * Create a new version of the PSCO id @id in the host @hostname Returns the id of the new version
     * 
     * @param id
     * @param hostName
     * @return
     * @throws StorageException
     */
    public static String newVersion(String id, boolean preserveSource, String hostName) throws StorageException, IOException, ClassNotFoundException {
        Object obj = getByID(id);
        String new_id = UUID.randomUUID().toString();
        makePersistent(obj, new_id);
        if(!preserveSource) {
            removeById(id);
        }
        return new_id;
    }

    /**
     * Returns the object with id @id This function retrieves the object from any location
     * 
     * @param id
     * @return
     * @throws StorageException
     */
    public static Object getByID(String id) throws StorageException, IOException, ClassNotFoundException {
        byte[] serializedObject = redisConnection.get(id.getBytes());
        Object ret = Serializer.deserialize(serializedObject);
        ((StorageObject)ret).setID(id);
        return ret;
    }

    /**
     * Executes the task into persistent storage
     * 
     * @param id 
     * @param descriptor
     * @param values
     * @param hostName
     * @param callback
     * @return
     * @throws StorageException
     */
    public static String executeTask(String id, String descriptor, Object[] values, String hostName, CallbackHandler callback)
            throws StorageException {
        return null;
    }

    /**
     * Retrieves the result of persistent storage execution
     * 
     * @param event
     * @return
     */
    public static Object getResult(CallbackEvent event) throws StorageException {
        return null;
    }

    /**
     * Consolidates all intermediate versions to the final id
     * 
     * @param idFinal
     * @throws StorageException
     */
    public static void consolidateVersion(String idFinal) throws StorageException {
        LOGGER.info("Consolidating version for " + idFinal);

        // Nothing to do in this dummy implementation
    }

    /*
     * ****************************************************************************************************************
     * SPECIFIC IMPLEMENTATION METHODS
     *****************************************************************************************************************/
    /**
     * Stores the object @o in the persistent storage with id @id
     * 
     * @param o
     * @param id
     * @throws StorageException
     */
    public static void makePersistent(Object o, String id) throws StorageException, IOException {
        byte[]  serializedObject = Serializer.serialize(o);
        String result = redisConnection.set(id.getBytes(), serializedObject);
        if(!result.equals("OK")) {
            throw new StorageException("Redis returned an error while trying to store object with id " + id);
        }
    }

    /**
     * Removes all the occurrences of a given @id
     * 
     * @param id
     */
    public static void removeById(String id) {
        redisConnection.del(id.getBytes());
    }


    // ONLY FOR TESTING PURPOSES
    static class MyStorageObject extends StorageObject implements  Serializable {
        private String innerString;

        public MyStorageObject(String myString) {
            innerString = myString;
        }

        public String getInnerString() {
            return innerString;
        }

        public void setInnerString(String innerString) {
            this.innerString = innerString;
        }
    }

    /**
     * ONLY FOR TESTING PURPOSES
     * @param args
     */
    public static void main(String[] args) throws ClassNotFoundException {
        try {
            init("/home/srodrig1/svn/compss/framework/trunk/utils/storage/redisPSCO/scripts/sample_hosts");
            MyStorageObject myObject = new MyStorageObject("This is an object");
            StorageItf.makePersistent(myObject, "prueba");
            Object retrieved = StorageItf.getByID("prueba");
            System.out.println(((MyStorageObject)retrieved).getInnerString());
            StorageItf.removeById("prueba");
        } catch(StorageException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
