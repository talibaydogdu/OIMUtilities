package com.blogspot.oraclestack.utilities;

import java.util.HashSet;
import java.util.Set;
import oracle.core.ojdl.logging.ODLLevel;
import oracle.core.ojdl.logging.ODLLogger;
import oracle.iam.platform.OIMClient;
import oracle.iam.platformservice.api.PlatformUtilsService;
import oracle.iam.platformservice.exception.InvalidCacheCategoryException;
import oracle.iam.platformservice.exception.PlatformServiceException;
import oracle.iam.platformservice.vo.JarElement;
import com.blogspot.oraclestack.constants.JarElementType;;

/**
 * This class contain methods that have the same functionality as the out of the box 
 * scripts given in $MW_HOME/Oracle_IDM1/server/bin/" directory.
 * @author rayedchan
 */
public class PlatformServiceUtilities 
{
    // Logger
    public static ODLLogger logger = ODLLogger.getODLLogger(PlatformServiceUtilities.class.getName());
            
    private PlatformUtilsService platformUtilsServiceOps = null;
    
    /**
     * Constructor
     * Precondition: A system administrator is logged in with the OIM client
     * @param oimClient 
     */
    public PlatformServiceUtilities(OIMClient oimClient)
    {
        this.platformUtilsServiceOps = oimClient.getService(PlatformUtilsService.class);
    }
    
    /**
    * Purges the entire OIM cache. Same functionality as the "PurgeCache.sh" script
    * which is located in "$MW_HOME/Oracle_IDM1/server/bin/" directory.
    * @throws InvalidCacheCategoryException 
    */
    public void purgeCache() throws InvalidCacheCategoryException
    {
        platformUtilsServiceOps.purgeCache("ALL");
        logger.log(ODLLevel.NOTIFICATION, "Successfully purged the cache.");
    }
    
    /**
     * Uploads a single jar file to database. A record will be added to OIM.OIMHOME_JARS 
     * table to indicate the jar is successfully uploaded. Same functionality as the "UploadJars.sh" script 
     * which is located in "$MW_HOME/Oracle_IDM1/server/bin/" directory.
     * @param jarType   The type of jar to be uploaded.
     * @param jarPath   The absolute path to the jar file that is being uploaded.
     */
    public void uploadJar(JarElementType jarType, String jarPath) throws PlatformServiceException
    {
        // Build a jar element containing path and type data
        JarElement jarElement = new JarElement();
        jarElement.setType(jarType.name());
        jarElement.setPath(jarPath);
        
        // Build a set object to put jar element
        Set<JarElement> jarElements = new HashSet<JarElement>(); 
        jarElements.add(jarElement);
        
        // Service to upload jar to OIM Schema
        platformUtilsServiceOps.uploadJars(jarElements);
        logger.log(ODLLevel.NOTIFICATION, "Successfully uploaded jar: Type = {0}, Path = {1}", new Object[]{jarType.name(), jarPath});
    }
    
    /**
     * Removes a jar from the database. The corresponding jar record will be removed
     * from OIM.OIMHOME_JARS table. Same functionality as the "DeleteJars.sh" script 
     * which is located in "$MW_HOME/Oracle_IDM1/server/bin/" directory.
     * @param jarType   The type of jar to be removed.
     * @param jarName   The name of the jar in the backend. Use the value in OIM.OIMHOME_JARS.OJ_NAME column.
     * @throws PlatformServiceException 
     */
    public void deleteJar(JarElementType jarType, String jarName) throws PlatformServiceException
    {   
        // Build a jar element containing path and type data
        JarElement jarElement = new JarElement();
        jarElement.setType(jarType.name());
        jarElement.setName(jarName);
        
        // Build a set object to put jar element
        Set<JarElement> jarElements = new HashSet<JarElement>(); 
        jarElements.add(jarElement);
        
        // Service to remove jar from OIM Schema
        platformUtilsServiceOps.deleteJars(jarElements);
        logger.log(ODLLevel.NOTIFICATION, "Successfully deleted jar: Type = {0}, Name = {1}", new Object[]{jarType.name(),jarName});
    }
    
     /**
     * Updates a single jar file to database. UPDATE_ON and UPDATED_BY columns in the OIMHOME_JARS table will be updated with current date
     * to indicate the jar is successfully updated. Same functionality as the "UpdateJars.sh" script 
     * which is located in "$MW_HOME/Oracle_IDM1/server/bin/" directory.
     * @param jarType   The type of jar to be updated.
     * @param jarPath   The absolute path to the jar file that is being updated. The jar name must exist in the database.
     */
    public void updateJar(JarElementType jarType, String jarPath) throws PlatformServiceException
    {
        // Build a jar element containing path and type data
        JarElement jarElement = new JarElement();
        jarElement.setType(jarType.name());
        jarElement.setPath(jarPath);
        
        // Build a set object to put jar element
        Set<JarElement> jarElements = new HashSet<JarElement>(); 
        jarElements.add(jarElement);
        
        // Service to update jar to OIM Schema
        platformUtilsServiceOps.updateJars(jarElements);
        logger.log(ODLLevel.NOTIFICATION, "Successfully updated jar: Type = {0}, Path = {1}", new Object[]{jarType.name(), jarPath});
    }
    
    /**
     * Download a jar from the database. Same functionality as the "DownloadJars.sh" script 
     * which is located in "$MW_HOME/Oracle_IDM1/server/bin/" directory.
     * @param jarType   The type of jar to be downloaded.
     * @param jarName   The name of the jar in the backend to be downloaded. Use the value in OIM.OIMHOME_JARS.OJ_NAME column.
     * @throws PlatformServiceException 
     */
    public void downloadJar(JarElementType jarType, String jarName, String destinationPath) throws PlatformServiceException
    {   
        // Build a jar element containing path and type data
        JarElement jarElement = new JarElement();
        jarElement.setType(jarType.name());
        jarElement.setName(jarName);
        jarElement.setPath(destinationPath);
        
        // Build a set object to put jar element
        Set<JarElement> jarElements = new HashSet<JarElement>(); 
        jarElements.add(jarElement);
        
        // Service to download jar from OIM Schema
        platformUtilsServiceOps.downloadJars(jarElements);
        logger.log(ODLLevel.NOTIFICATION, "Successfully downloaded jar: Type = {0}, Name = {1}", new Object[]{jarType.name(),jarName});
    }
}