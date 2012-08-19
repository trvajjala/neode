package org.neo4j.neode;

import static java.util.Arrays.asList;
import static org.neo4j.graphdb.DynamicRelationshipType.withName;

import java.util.Random;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.neode.logging.Log;
import org.neo4j.neode.properties.Property;

public class DatasetManager
{
    private final GraphDatabaseService db;
    private final Log log;
    private final Random random;

    public DatasetManager( GraphDatabaseService db, Log log )
    {
        this.db = db;
        this.log = log;
        random = new Random();
    }

    public NodeSpecification nodeSpecification( String label, Property... properties )
    {
        return new NodeSpecification( label, asList( properties ) );
    }

    public RelationshipSpecification relationshipSpecification( String label, Property... properties )
    {
        return relationshipSpecification( withName( label ), properties );
    }

    public RelationshipSpecification relationshipSpecification( RelationshipType relationshipType,
                                                                Property... properties )
    {
        return new RelationshipSpecification( relationshipType, asList( properties ) );
    }

    public Dataset newDataset( String description )
    {
        return new Dataset( description, db, log, random );
    }
}