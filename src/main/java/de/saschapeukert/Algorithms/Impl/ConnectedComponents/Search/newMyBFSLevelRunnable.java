package de.saschapeukert.Algorithms.Impl.ConnectedComponents.Search;

import de.saschapeukert.Algorithms.Abst.WorkerCallableTemplate;
import org.neo4j.graphdb.Direction;

import java.util.Set;

/**
 * Created by Sascha Peukert on 19.11.2015.
 */
public class newMyBFSLevelRunnable extends WorkerCallableTemplate {

    public Direction direction;

    public newMyBFSLevelRunnable(int startPos, int endPos, Long[] array, Direction direction, boolean output){
        super(startPos,endPos,array,output);

        this.direction = direction;
    }

    @Override
    protected void compute() {

        int currentPos=startPos;
        while(currentPos<endPos){
            long parentID = refArray[currentPos];
            Set<Long> q = expandNode(parentID,MyBFS.visitedIDs,true,direction);
            MyBFS.visitedIDs.add(parentID);

            if(MyBFS.nodeIDSet!=null){
                // this is a backward sweep

                q.add(parentID); // quick fix
                q.retainAll(MyBFS.nodeIDSet);
            }

            q.remove(parentID);

            returnSet.addAll(q);

            currentPos++;
        }

    }
}


