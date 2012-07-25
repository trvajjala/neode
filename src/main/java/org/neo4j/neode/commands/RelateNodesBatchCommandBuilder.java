package org.neo4j.neode.commands;

import static org.neo4j.neode.commands.AllowMultiple.allowMultiple;
import static org.neo4j.neode.numbergenerators.FlatDistributionUniqueRandomNumberGenerator.flatDistribution;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.neode.Dataset;
import org.neo4j.neode.DomainEntityInfo;
import org.neo4j.neode.commands.interfaces.Update;
import org.neo4j.neode.commands.interfaces.Cardinality;
import org.neo4j.neode.commands.interfaces.RelationshipName;
import org.neo4j.neode.commands.interfaces.To;
import org.neo4j.neode.finders.NodeFinderStrategy;
import org.neo4j.neode.logging.Log;
import org.neo4j.neode.numbergenerators.NumberGenerator;

public class RelateNodesBatchCommandBuilder implements To, RelationshipName, Cardinality, Update
{

    public static To relateEntities( DomainEntityInfo domainEntityInfo )
    {
        return new RelateNodesBatchCommandBuilder( domainEntityInfo );
    }

    private static final int DEFAULT_BATCH_SIZE = 10000;

    private DomainEntityInfo domainEntityInfo;
    private Range cardinality;
    private UniquenessStrategy uniquenessStrategy;
    private NodeFinderStrategy nodeFinderStrategy;
    private RelationshipType relationshipType;
    private Direction direction;

    private RelateNodesBatchCommandBuilder( DomainEntityInfo domainEntityInfo )
    {
        this.domainEntityInfo = domainEntityInfo;
    }

    @Override
    public RelationshipName to( NodeFinderStrategy nodeFinderStrategy )
    {
        this.nodeFinderStrategy = nodeFinderStrategy;
        return this;
    }

    @Override
    public Update cardinality( Range value )
    {
        cardinality = value;
        uniquenessStrategy = allowMultiple();
        return this;
    }

    @Override
    public Update cardinality( Range value, UniquenessStrategy uniqueness )
    {
        cardinality = value;
        uniquenessStrategy = uniqueness;
        return this;
    }

    @Override
    public Cardinality relationship( RelationshipType value )
    {
        relationshipType = value;
        direction = Direction.OUTGOING;
        return this;
    }

    @Override
    public Cardinality relationship( RelationshipType value, Direction direction )
    {
        relationshipType = value;
        this.direction = direction;
        return this;
    }

    @Override
    public DomainEntityInfo update( Dataset dataset, int batchSize )
    {
        RelateNodesBatchCommand command = new RelateNodesBatchCommand( domainEntityInfo, batchSize,
                relationshipType, direction, cardinality, uniquenessStrategy, nodeFinderStrategy, true );
        return dataset.execute( command );
    }

    @Override
    public DomainEntityInfo update( Dataset dataset )
    {
        return update( dataset, DEFAULT_BATCH_SIZE );
    }

    @Override
    public void updateNoReturn( Dataset dataset, int batchSize )
    {
        RelateNodesBatchCommand command = new RelateNodesBatchCommand( domainEntityInfo, batchSize,
                relationshipType, direction, cardinality, uniquenessStrategy, nodeFinderStrategy, false );
        dataset.execute( command );
    }

    @Override
    public void updateNoReturn( Dataset dataset )
    {
        updateNoReturn( dataset, DEFAULT_BATCH_SIZE );
    }


    private class RelateNodesBatchCommand implements BatchCommand
    {
        private final DomainEntityInfo startNodeDomainEntityInfo;
        private final int batchSize;
        private final Range cardinality;
        private final UniquenessStrategy uniquenessStrategy;
        private final NodeFinderStrategy nodeFinderStrategy;
        private final RelationshipType relationshipType;
        private final Direction direction;
        private final NumberGenerator numberOfRelsGenerator;
        private final boolean captureEndNodeIds;
        private long totalRels = 0;
        private Set<Long> endNodeIds = new HashSet<Long>();

        public RelateNodesBatchCommand( DomainEntityInfo startNodeDomainEntityInfo, int batchSize,
                                        RelationshipType relationshipType,
                                        Direction direction, Range cardinality, UniquenessStrategy uniquenessStrategy,
                                        NodeFinderStrategy nodeFinderStrategy, boolean captureEndNodeIds )
        {
            this.startNodeDomainEntityInfo = startNodeDomainEntityInfo;
            this.batchSize = batchSize;
            this.relationshipType = relationshipType;
            this.direction = direction;
            this.cardinality = cardinality;
            this.uniquenessStrategy = uniquenessStrategy;
            this.nodeFinderStrategy = nodeFinderStrategy;
            this.captureEndNodeIds = captureEndNodeIds;

            numberOfRelsGenerator = flatDistribution();
        }

        @Override
        public int numberOfIterations()
        {
            return startNodeDomainEntityInfo.nodeIds().size();
        }

        @Override
        public int batchSize()
        {
            return batchSize;
        }

        @Override
        public void execute( GraphDatabaseService db, int index, Random random )
        {
            Node firstNode = db.getNodeById( startNodeDomainEntityInfo.nodeIds().get( index ) );

            int numberOfRels = numberOfRelsGenerator.generateSingle( cardinality.min(), cardinality.max(), random );
            totalRels += numberOfRels;

            Iterable<Node> nodes = nodeFinderStrategy.getNodes( db, firstNode, numberOfRels, random );
            for ( Node secondNode : nodes )
            {
                if ( captureEndNodeIds )
                {
                    endNodeIds.add( secondNode.getId() );
                }
                uniquenessStrategy.apply( db, firstNode, secondNode, relationshipType, direction );
            }
        }

        @Override
        public String description()
        {
            return String.format( "Creating '%s' relationships.", shortDescription() );
        }

        @Override
        public String shortDescription()
        {
            String relStart = "-";
            String relEnd = "->";
            if ( direction.equals( Direction.INCOMING ) )
            {
                relStart = "<-";
                relEnd = "-";
            }
            return String.format( "(%s)%s[:%s]%s(%s)", startNodeDomainEntityInfo.entityName(), relStart,
                    relationshipType.name(), relEnd, nodeFinderStrategy.entityName() );
        }

        @Override
        public void onBegin( Log log )
        {
            log.write( String.format( "      [Min: %s, Max: %s, Uniqueness: %s]", cardinality.min(), cardinality.max(),
                    uniquenessStrategy.description() ) );
        }

        @Override
        public void onEnd( Log log )
        {
            log.write( String.format( "      [Avg: %s relationship(s) per %s]",
                    totalRels / startNodeDomainEntityInfo.nodeIds().size(), startNodeDomainEntityInfo.entityName() ) );
        }

        @Override
        public DomainEntityInfo results()
        {
            return new DomainEntityInfo( nodeFinderStrategy.entityName(), new ArrayList<Long>( endNodeIds ) );
        }
    }
}