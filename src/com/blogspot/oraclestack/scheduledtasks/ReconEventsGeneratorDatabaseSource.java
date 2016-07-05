package com.blogspot.oraclestack.scheduledtasks;

import Thor.API.Exceptions.tcAPIException;
import Thor.API.Exceptions.tcColumnNotFoundException;
import Thor.API.Exceptions.tcInvalidLookupException;
import Thor.API.Operations.tcLookupOperationsIntf;
import Thor.API.tcResultSet;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import oracle.core.ojdl.logging.ODLLevel;
import oracle.core.ojdl.logging.ODLLogger;
import oracle.iam.platform.Platform;
import oracle.iam.reconciliation.api.BatchAttributes;
import oracle.iam.reconciliation.api.ChangeType;
import oracle.iam.reconciliation.api.InputData;
import oracle.iam.reconciliation.api.ReconOperationsService;
import oracle.iam.reconciliation.api.ReconciliationResult;
import oracle.iam.scheduler.vo.TaskSupport;

/**
 * A scheduled task to create reconciliation events of a specified resource object
 * using data from a database table. The database table must be constructed in a way where
 * the column names match the names of the corresponding reconciliation field. Either 
 * trusted or target resource object can be used as long as the required fields are provided. 
 * @author rayedchan
 * 
 * Additional features:
 * - Attribute Mapping Translation
 * - Child Data 
 * TODO: Put batching
 * TODO: Action Date
 */
public class ReconEventsGeneratorDatabaseSource extends TaskSupport
{
    // Logger
    private static final ODLLogger LOGGER = ODLLogger.getODLLogger(ReconEventsGeneratorDatabaseSource.class.getName());
    
    // OIM API Services
    private ReconOperationsService reconOps = Platform.getService(ReconOperationsService.class);
    
    // Default Date Format
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
   
    @Override
    public void execute(HashMap params) throws NamingException, SQLException, tcColumnNotFoundException, tcInvalidLookupException, tcAPIException
    {
        LOGGER.log(ODLLevel.NOTIFICATION, "Scheduled Job Parameters: {0}", new Object[]{params});
        Connection conn = null;
        tcLookupOperationsIntf lookupOps = null;
                
        try
        {
            // Get the parameters from the scheduled job
            String dataSource = (String) params.get("Data Source"); // JNDI Name for the Data Source
            String resourceObjectName = (String) params.get("Resource Object Name"); // Reconciliation Profile Name
            String tableName = (String) params.get("Table Name"); // Database table name
            String filter = (String) params.get("Filter") == null ? "" : (String) params.get("Filter"); // WHERE clause filter
            String dateFormat = (String) params.get("Date Format") == null ? DEFAULT_DATE_FORMAT : (String) params.get("Date Format"); // Date Format for reconciliation event E.g. "yyyy-MM-dd"
            Boolean ignoreDuplicateEvent = (Boolean) params.get("Ignore Duplicate Event"); // Identical to using IgnoreEvent API; if true, reconciliation event won't be created if there is nothing to update
            String attrMappings = (String) params.get("Mapping Lookup"); // Correlates target field to recon field 
            String itResName = (String) params.get("IT Resource Name"); // IT Resource Name required for target sources. Empty for trusted sources.
            
            // Parameters for Child Data
            String linkColumnName = (String) params.get("Link Column Name"); // Field to relate parent and child table
            
            // Reconciliation events details
            Boolean eventFinished = true; // No child data provided; mark event to Data Received
            Date actionDate = null; // Event to be processed immediately for null. If a date is specified, defer reconciliation event.
            BatchAttributes batchAttrs = new BatchAttributes(resourceObjectName, dateFormat, ignoreDuplicateEvent);
            
            // Get database connection from data source
            conn = getDatabaseConnection(dataSource);
            LOGGER.log(ODLLevel.NOTIFICATION, "Retrieved connection for datasource: {0}" , new Object[]{dataSource});
            
            // Fetch Recon Attr Map Lookup if any
            lookupOps = Platform.getService(tcLookupOperationsIntf.class);
            HashMap<String,String> reconAttrMap = convertLookupToMap(lookupOps, attrMappings);
            LOGGER.log(ODLLevel.NOTIFICATION, "Lookup {0} : {1}" , new Object[]{attrMappings, reconAttrMap});
            
            // Derive child table mappings
            HashMap<String,String> childTableMappings = new HashMap<String,String>(); // Key = Multivalued Field Name, Code = Target Child Table Name
            HashMap<String,HashMap<String,String>> childColumnMappings = new HashMap<String,HashMap<String,String>>(); // Key = Multivalued Field Name, {Key = Field Name, Value = Target Column Name}
            deriveChildTableMappings(reconAttrMap, childTableMappings, childColumnMappings);
            
            // Construct list of reconciliation event to be created reading from source database table
            List<InputData> allReconEvents = constructReconciliationEventList(conn, tableName, filter, eventFinished, actionDate, reconAttrMap, itResName, childTableMappings, childColumnMappings, linkColumnName);
            LOGGER.log(ODLLevel.NOTIFICATION, "Recon Events {0}: {1}", new Object[]{allReconEvents.size(), allReconEvents});
            InputData[] events = new InputData[allReconEvents.size()];
            allReconEvents.toArray(events);
            
            // Create reconciliation events in OIM and process them
            ReconciliationResult result = reconOps.createReconciliationEvents(batchAttrs, events);
            LOGGER.log(ODLLevel.NOTIFICATION, "Success result: {0}",  new Object[]{result.getSuccessResult()});
            LOGGER.log(ODLLevel.NOTIFICATION, "Success result: {0}",  new Object[]{result.getFailedResult()});
        } 
        
        catch (tcAPIException e) 
        { 
            LOGGER.log(ODLLevel.SEVERE, "Could not get lookup: ", e);
            throw e;
        } 
        
        catch (tcInvalidLookupException e)
        {
            LOGGER.log(ODLLevel.SEVERE, "Could not get lookup: ", e);
            throw e;
        }
        
        catch (tcColumnNotFoundException e) 
        {
            LOGGER.log(ODLLevel.SEVERE, "Could not get lookup: ", e);
            throw e;
        }
        
        catch (SQLException e) 
        {       
            LOGGER.log(ODLLevel.SEVERE, "Could not get database connection: ", e);
            throw e;
        } 
           
        catch (NamingException e) 
        {
            LOGGER.log(ODLLevel.SEVERE, "Could not get database connection: ", e);
            throw e;
        }
        
        finally
        {
            if(conn != null)
            {
                conn.close();
            }
            
            if(lookupOps != null)
            {
                lookupOps.close();
            }
        }
    }

    @Override
    public HashMap getAttributes() 
    {
        return null;
    }

    @Override
    public void setAttributes() 
    {
        
    }
    
    /**
     * Derive Child Table Mappings
     * @param reconAttrMap  Reconciliation Filed Mappings; Remove child info once processed
     * @param childTableMappings Populate reconciliation map name with corresponding child table name
     * @param childColumnMappings Populate child reconciliation field name with corresponding child column name 
     */
    private void deriveChildTableMappings(HashMap<String,String> reconAttrMap, HashMap<String,String> childTableMappings, HashMap<String,HashMap<String,String>> childColumnMappings)
    {
        // Iterator for Recon Attr Lookup
        Iterator<Entry<String,String>> it  = reconAttrMap.entrySet().iterator();

        // Iterate each entry and only getting child info
        while(it.hasNext())
        {
            Map.Entry<String,String> pair = (Map.Entry) it.next();
            
            // Child mapping detected
            if(pair.getKey().contains("~"))
            {
                String key = pair.getKey(); // Reconcilition Multivauled Name and Field E.g. BadgeUD_ACCESS Child~Name
                String value = pair.getValue(); // Target table and column E.g. MY_BADGE_ENT_RE_FEED~NAME

                String[] reconMapAndField = key.split("~");
                String[] targetTableAndColumn = value.split("~");

                String rfMultiValueName = reconMapAndField[0];
                String targetChildTable = targetTableAndColumn[0];

                String childFieldName = reconMapAndField[1];
                String childColumnName = targetTableAndColumn[1];

                // Multivalued Field Map exist already; adding additional column
                if(childTableMappings.containsKey(rfMultiValueName))
                {
                    childColumnMappings.get(rfMultiValueName).put(childFieldName, childColumnName);  
                }

                // First time inspecting child table
                else
                {
                    childTableMappings.put(rfMultiValueName, targetChildTable); // Put Multivalued Field and target table name

                    HashMap<String,String> fieldToColumnMap = new HashMap<String,String>();
                    fieldToColumnMap.put(childFieldName, childColumnName);

                    childColumnMappings.put(rfMultiValueName, fieldToColumnMap); // Put Multivalued Map Name and field to column correspondence
                }
                
                // Remove child info from Recon Attr Map
                reconAttrMap.remove(key);
            }
        }
    }
    
    /**
     * Get database connection from a data source
     * using the JNDI Name
     * @param jndiName  JNDI Name
     * @return Database Connection Object
     * @throws NamingException
     * @throws SQLException 
     **/
    private Connection getDatabaseConnection(String jndiName) throws NamingException, SQLException 
    {
        Context initContext = new InitialContext();
        DataSource ds = (DataSource)initContext.lookup(jndiName);
        Connection connection  = ds.getConnection();
        return connection;
    }
    
    /**
     * Construct a list of reconciliation events staging to be created
     * @param conn  Database connection
     * @param tableName Source table name
     * @param filter    WHERE clause to be appended to SQL query
     * @param eventFinished Determine if child data needs to be added
     * @param actionDate For deferring events
     * @param reconAttrMap Reconciliation Attribute Mappings
     * @param itResName IT Resource Name; Used for target resources; Empty for trusted
     * @param childTableMappings Map of Reconciliation Field Map Name to Target Table Name 
     * @param childColumnMappings Map of Reconciliation Field Map Name and corresponding field name to target column name
     * @param linkColumnName Column to link parent to child table
     * @return List of events to be created
     * @throws SQLException 
     */
    public List<InputData> constructReconciliationEventList(Connection conn, String tableName, String filter, Boolean eventFinished, Date actionDate, HashMap<String,String> reconAttrMap, String itResName, HashMap<String,String> childTableMappings, HashMap<String,HashMap<String,String>> childColumnMappings, String linkColumnName) throws SQLException
    {
        List<InputData> allReconEvents = new ArrayList<InputData>();
        
        // SELECT SQL Query on source table
        String usersQuery = "SELECT * FROM " + tableName + (filter == null || "".equals(filter) ? "" : " " + filter);
        PreparedStatement ps = conn.prepareStatement(usersQuery);
        ResultSet rs = ps.executeQuery();

        // Get the result set metadata
        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        LOGGER.log(ODLLevel.NOTIFICATION, "Column count: {0}", new Object[]{columnCount});
        
        // Correlate target column with recon field name
        boolean useTranslateMap = !reconAttrMap.isEmpty(); // Use lookup to get mappings if not empty; otherwise assume target column names are identical to the recon field names

        // Iterate each record
        while(rs.next())
        {
            // Store recon event data 
            HashMap<String, Serializable> reconEventData = new HashMap<String, Serializable>();

            // Use Lookup to translate mappings
            if(useTranslateMap)
            {
                // Iterate Attr Mappings Lookup
                for(Map.Entry<String,String> entry : reconAttrMap.entrySet())
                {
                    String reconFieldName = entry.getKey(); // Code Key
                    String targetColumnName = entry.getValue(); // Decode
                                       
                    // IT Resource Name Field; Only for target
                    if("__SERVER__".equals(targetColumnName))
                    {
                        reconEventData.put(reconFieldName, itResName);
                    }
                    
                    // All other attributes
                    else
                    {
                        String value = rs.getString(targetColumnName); // Get column value
                        reconEventData.put(reconFieldName, value);
                    }
                }
            }
            
            // Target columns are identical to reconciliation field names
            // No Child data supported for this optional
            else
            {
                // Iterate each column and populate map accordingly
                for(int i = 1; i <= columnCount; i++)
                {
                    String reconFieldName = rsmd.getColumnName(i); // Get column name
                    String value = rs.getString(reconFieldName); // Get column value
                    reconEventData.put(reconFieldName, value);
                }
            }

            LOGGER.log(ODLLevel.NOTIFICATION, "Recon Event Data: {0}", new Object[]{reconEventData});
            InputData event = new InputData(reconEventData, null, eventFinished, ChangeType.CHANGELOG, actionDate);

            // Add recon event to list
            allReconEvents.add(event);
        }
        
        return allReconEvents;
    }
    
    private HashMap<String,ArrayList<HashMap<String,Object>>> fetchUserEntitlements(Connection conn, HashMap<String,String> childTableMappings, HashMap<String,HashMap<String,String>> childColumnMappings, String linkColumnName, String linkColumnValue) throws SQLException
    { 
        HashMap<String,ArrayList<HashMap<String,Object>>> childReconData = new HashMap<String,ArrayList<HashMap<String,Object>>>(); // {Key = Child Recon Field Map Name, Value = {Key = Child Recon Field Name, Value = data}} 
         
        // Iterate each child table
        for(Map.Entry<String,String> entry : childTableMappings.entrySet())
        {
            String rfMapName = entry.getKey();
            String childTableName = entry.getValue();
            
            // Fetch user entitlement records from child table
            String userEntQuery = "SELECT * FROM " + childTableName + " WHERE " + linkColumnName + " = ?";
            PreparedStatement ps = conn.prepareStatement(userEntQuery);
            ps.setString(1, linkColumnValue);
            
            ResultSet rs = ps.executeQuery();
            ArrayList<HashMap<String,Object>> childEntries = new ArrayList<HashMap<String,Object>>();
            
            // iterate result set
            while(rs.next())
            {
                HashMap<String,String> columnMap = childColumnMappings.get(rfMapName);
                HashMap<String,Object> childRecordData = new HashMap<String, Object>();
                
                for(Map.Entry<String,String> cEntry : columnMap.entrySet())
                {
                    String rfName = cEntry.getKey();
                    String columnName = cEntry.getValue();
                    
                    String columnValue = rs.getString(columnName);
                    childRecordData.put(rfName, columnValue); // Populate child recon field with corresponding target column
                }
                
                childEntries.add(childRecordData);   
            }
            
            childReconData.put(rfMapName, childEntries);
        }
        
        return childReconData;
    }
    
    /**
     * Converts a lookup definition into a Map. The Code Key column is used as
     * the key and the Decode column is used as the value.
     * @param lookupDefinitionName      Name of the lookup definition
     * @return Map of lookup values {Key = Code Key, Value = Decode}.
     * @throws tcAPIException
     * @throws tcInvalidLookupException
     * @throws tcColumnNotFoundException
     */
    public HashMap<String, String> convertLookupToMap(tcLookupOperationsIntf lookupOps, String lookupDefinitionName) throws tcAPIException, tcInvalidLookupException, tcColumnNotFoundException 
    {
        HashMap<String, String> lookupValues = new HashMap<String, String>();
        
        if(lookupDefinitionName != null && !lookupDefinitionName.equalsIgnoreCase(""))
        {
            tcResultSet lookupValuesRs = lookupOps.getLookupValues(lookupDefinitionName); // Get lookup values
            int numRows = lookupValuesRs.getTotalRowCount();

            // Iterate lookup resultset and construct map
            for (int i = 0; i < numRows; i++) 
            {
                lookupValuesRs.goToRow(i);
                String codeKey = lookupValuesRs.getStringValue("Lookup Definition.Lookup Code Information.Code Key"); // Fetch Code Key
                String decode = lookupValuesRs.getStringValue("Lookup Definition.Lookup Code Information.Decode"); // Fetch Decode
                lookupValues.put(codeKey, decode);
            }
        }
        
        return lookupValues;
    }
}