package de.saschapeukert.database;

import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.LongHashSet;
import de.saschapeukert.Starter;
import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.cursor.Cursor;
import org.neo4j.graphdb.*;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.api.DataWriteOperations;
import org.neo4j.kernel.api.ReadOperations;
import org.neo4j.kernel.api.cursor.NodeItem;
import org.neo4j.kernel.api.cursor.RelationshipItem;
import org.neo4j.kernel.api.exceptions.EntityNotFoundException;
import org.neo4j.kernel.api.exceptions.InvalidTransactionTypeKernelException;
import org.neo4j.kernel.api.exceptions.schema.ConstraintValidationKernelException;
import org.neo4j.kernel.api.properties.DefinedProperty;
import org.neo4j.kernel.api.properties.Property;
import org.neo4j.kernel.impl.api.store.RelationshipIterator;
import org.neo4j.kernel.impl.core.ThreadToStatementContextBridge;
import org.neo4j.kernel.impl.store.NeoStores;
import org.neo4j.management.Neo4jManager;

import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This class encapsulates all access to the neo4j database.
 * <br>
 * Created by Sascha Peukert on 31.08.2015.
 */
@SuppressWarnings("deprecation")
public class DBUtils {

    private NeoStores neoStore;
    private GraphDatabaseService graphDb;
    private  ThreadToStatementContextBridge ctx=null;
    public  long highestNodeKey;
    public  long highestRelationshipKey;

    public Node getSomeRandomNode( ThreadLocalRandom random){
        long r;
        while(true) {
            try {
                // NEW VERSION, checks Map for ID and not DB
                r = random.nextLong(highestNodeKey);
                if(Starter.resultCounterContainsKey(r)){
                    return graphDb.getNodeById(r);
                }
            } catch (NotFoundException e){
                // this should never be happening!
                System.out.println("Something terrible is happend");
            }
        }
    }

    public ReadOperations getReadOperations(){
        return ctx.get().readOperations();
    }

    public DataWriteOperations getDataWriteOperations()
    {
        try {
            return ctx.get().dataWriteOperations();
        } catch (InvalidTransactionTypeKernelException e) {
            e.printStackTrace();
        }
        return null;
    }

    public long getSomeRandomNodeId(ThreadLocalRandom random){
        long r;
        while(true) {
            r = random.nextLong(highestNodeKey);
            // NEW VERSION without DB-Lookup
            if(Starter.resultCounterContainsKey(r))
                return r;
        }
    }

    public Relationship getSomeRandomRelationship(ThreadLocalRandom random, int highestNodeId){
        long r;
        while(true) {
            try {
                r = (long) random.nextInt(highestNodeId);
                Node n = graphDb.getNodeById(r);  // meh?
                return n.getRelationships(Direction.BOTH).iterator().next();
            } catch (NotFoundException | NoSuchElementException ex){
                // NEXT!
            }
        }
    }

    public boolean removePropertyFromAllNodes(int PropertyID, DataWriteOperations ops){
        PrimitiveLongIterator it = getPrimitiveLongIteratorForAllNodes();
        try {
            while(it.hasNext()){
                ops.nodeRemoveProperty(it.next(),PropertyID);
            }
        } catch (EntityNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     *  ONLY REMOVES THE KEY!
     *  You have to remove the Property from every node too
     * @param propertyID
     * @param ops
     */
    public void removePropertyKey(int propertyID, DataWriteOperations ops){
        ops.graphRemoveProperty(propertyID);
    }

    public boolean createPropertyAtNode(long nodeID, Object value, int PropertyID, DataWriteOperations ops){

        try {
            DefinedProperty prop;
            if(value instanceof Integer){
                prop = Property.intProperty(PropertyID, (int)value);
            } else if(value instanceof String){
                prop = Property.stringProperty(PropertyID, (String)value);
            } else if(value instanceof  Long){
                prop = Property.longProperty(PropertyID, (long)value);
            } else if(value instanceof Float) {
                prop = Property.floatProperty(PropertyID, (float)value);
            } else if(value instanceof Double) {
                prop = Property.doubleProperty(PropertyID, (double)value);
            }  else if(value instanceof Boolean) {
                prop = Property.booleanProperty(PropertyID, (boolean)value);

            }else{
                System.out.println("I don't know this property! "+ value.getClass().getName() );
                return false;
            }
            ops.nodeSetProperty(nodeID, prop);

        } catch (EntityNotFoundException | ConstraintValidationKernelException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void refreshHighestNodeID(){
        highestNodeKey = Neo4jManager.get().getPrimitivesBean().getNumberOfNodeIdsInUse();
        //getStoreAcess().getNodeStore().getHighId();
    }

    public void refreshHighestRelationshipID(){
        highestRelationshipKey = Neo4jManager.get().getPrimitivesBean().getNumberOfRelationshipIdsInUse();
        //getStoreAcess().getRelationshipStore().getHighId();
    }

    private long getNextPropertyID(){
        return getStoreAcess().getPropertyStore().nextId();
    }

    private NeoStores getStoreAcess(){
        if(neoStore==null)
            neoStore = ((GraphDatabaseAPI)graphDb).getDependencyResolver().resolveDependency( NeoStores.class );
        return neoStore;
    }

    /*public  ResourceIterator<Node> getIteratorForAllNodes( ) {
        GlobalGraphOperations ggo = GlobalGraphOperations.at(graphDb);
        ResourceIterable<Node> allNodes = ggo.getAllNodes();
        return allNodes.iterator();
    }*/

    public  PrimitiveLongIterator getPrimitiveLongIteratorForAllNodes( ) {
        PrimitiveLongIterator it = getReadOperations().nodesGetAll();
        return it;
    }

    /**
     * Gets the PropertyID for a given PropertyName or creates a new ID for that name and returns it.
     * @param propertyName HAS TO BE UNIQUE
     * @return -1 if error happend
     */
    public int GetPropertyID(String propertyName){
        try(Transaction tx = graphDb.beginTx()) {
            DataWriteOperations ops = ctx.get().dataWriteOperations();

            int id =ops.propertyKeyGetOrCreateForName(propertyName);
            tx.success();
            return  id;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1; // ERROR happend
    }

    /**
     *
     * @param ops
     * @param nodeID
     * @param dir
     * @return returns all connectedNodeIds, possibly even the original nodeID itself
     */
    public LongHashSet getConnectedNodeIDs(ReadOperations ops, long nodeID, Direction dir){
        LongHashSet it = new LongHashSet();
        try {
            RelationshipIterator itR = ops.nodeGetRelationships(nodeID, dir);
            while(itR.hasNext()){
                long rID = itR.next();
                Cursor<RelationshipItem> relCursor = ops.relationshipCursor(rID);
                while(relCursor.next()){
                    RelationshipItem item = relCursor.get();
                    it.add(item.otherNode(nodeID));
                }
                relCursor.close();
            }

        } catch (EntityNotFoundException e) {
            e.printStackTrace();
        }
        return it;
    }

    public LongArrayList getConnectedNodeIDsAsList(ReadOperations ops, long nodeID, Direction dir){
        LongArrayList it = new LongArrayList(10000);
        try {
            RelationshipIterator itR = ops.nodeGetRelationships(nodeID, dir);
            while(itR.hasNext()){
                long rID = itR.next();
                Cursor<RelationshipItem> relCursor = ops.relationshipCursor(rID);
                while(relCursor.next()){
                    RelationshipItem item = relCursor.get();
                    it.add(item.otherNode(nodeID));
                }
                relCursor.close();
            }

        } catch (EntityNotFoundException e) {
            e.printStackTrace();
        }
        return it;
    }

    public Transaction openTransaction(){
        return graphDb.beginTx();
    }

    public void closeTransactionWithSuccess(Transaction tx){
        tx.success();
            tx.close();
    }

    public boolean loadNode(long id){
        try{
            Cursor<NodeItem> c =getReadOperations().nodeCursor(id);
            c.next();
            //c.get().id();
            c.close();
            //graphDb.getNodeById(id);
            return true;
        } catch (Exception e){
            return false;
        }
    }

    public boolean loadRelationship(long id){
        try{
            Cursor<RelationshipItem> c = getReadOperations().relationshipCursor(id);
            c.next();
            //c.get().id();
            c.close();
            //graphDb.getRelationshipById(id);
            return true;
        } catch (Exception e){
            return false;
        }
    }

    /**
     *
     * @param id
     * @param direction
     * @return -1 if EntityNotFound
     */
    public int getDegree(long id, Direction direction)
    {
        try {
            return getReadOperations().nodeGetDegree(id,direction);
        } catch (EntityNotFoundException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     *
     * @param id
     * @return -1 if EntityNotFound
     */
    public int getDegree(long id)
    {
        return getDegree(id,Direction.BOTH);
    }

    public DBUtils(GraphDatabaseService graphDb){
        this.graphDb = graphDb;
        ctx = ((GraphDatabaseAPI) graphDb).getDependencyResolver().resolveDependency
                (ThreadToStatementContextBridge.class);
        refreshHighestNodeID();
        refreshHighestRelationshipID();
    }

    public void registerShutdownHook( )
    {
        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running application).
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                shutdownDB();
            }
        });
    }

    public Result executeQuery(String query){
        return graphDb.execute(query);
    }

    public void shutdownDB(){
        System.out.println("Shutting down neo4j.");
        try {
            graphDb.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
            graphDb.shutdown();
        } finally {
            System.out.println("Shutting down neo4j complete.");
        }
    }
}
