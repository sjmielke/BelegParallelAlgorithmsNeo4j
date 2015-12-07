package de.saschapeukert.Algorithms.Abst;

import org.neo4j.graphdb.Direction;
import org.neo4j.kernel.api.ReadOperations;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Sascha Peukert on 19.11.2015.
 */
public abstract class WorkerCallableTemplate extends MyBaseCallable {

    protected Long[] refArray;
    protected int startPos; //incl.
    protected int endPos; // not incl.

    protected Set<Long> returnSet;
    private ReadOperations ops;

    protected WorkerCallableTemplate(int startPos, int endPos, Long[] array) {

        this.refArray = array;
        this.startPos = startPos;
        this.endPos = endPos;
        returnSet = new HashSet<>(10000);
    }

    // Children must overwrite work()

    public Object call() throws Exception {
        tx = db.openTransaction();
        ops = db.getReadOperations(); // needs to be in a TA
        work();
        db.closeTransactionWithSuccess(tx);
        return returnSet;
    }

    protected Set<Long> expandNode(Long id, Collection c, boolean ignoreIfCollectionsContainsItem, Direction dir){
        Set<Long> resultSet = expandNode(id,dir);
                //new HashSet<>(db.getConnectedNodeIDs(ops, id, dir));
        if(ignoreIfCollectionsContainsItem){
            // nicht aufnehmen
            resultSet.removeAll(c);
        } else{
            // nur aufnehmen, wenn drin
            resultSet.retainAll(c);
        }
        return resultSet;
    }

    protected Set<Long> expandNode(Long id, Direction dir){
        Set<Long> resultSet = new HashSet<>(db.getConnectedNodeIDs(ops, id, dir));
        return resultSet;
    }

}
