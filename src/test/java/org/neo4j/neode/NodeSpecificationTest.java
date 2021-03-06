package org.neo4j.neode;

import java.util.Collections;

import org.junit.Test;

import org.neo4j.graphdb.*;
import org.neo4j.neode.properties.Property;
import org.neo4j.neode.test.Db;

import static java.util.Arrays.asList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NodeSpecificationTest
{
    @Test
    public void shouldCreateNewNode() throws Exception
    {
        // given
        GraphDatabaseService db = Db.impermanentDb();
        Label user = DynamicLabel.label("user");
        Node node;
        try ( Transaction tx = db.beginTx() )
        {
            NodeSpecification nodeSpecification = new NodeSpecification( user, Collections.<Property>emptyList(), db );

            // when
            node = nodeSpecification.build( 1 );
            tx.success();
        }

        // then
        try ( Transaction tx = db.beginTx() )
        {
            assertNotNull( node );
            assertEquals( true, node.hasLabel( user ) );
            tx.success();
        }
    }

    @Test
    public void shouldApplyPropertiesToNewNode() throws Exception
    {
        // given
        GraphDatabaseService db = Db.impermanentDb();
        Node node;
        try ( Transaction tx = db.beginTx() ) {

            Property property = new Property()
            {
                @Override
                public void setProperty(PropertyContainer propertyContainer, String label,
                                        int iteration)
                {
                    propertyContainer.setProperty( "myproperty", "value" );
                }
            };
            NodeSpecification nodeSpecification = new NodeSpecification( "user", asList( property ), db );

            // when
            node = nodeSpecification.build( 1 );
            tx.success();
        }

        // then
        try ( Transaction tx = db.beginTx() ) {
            assertEquals( "value", node.getProperty( "myproperty" ) );
            tx.success();
        }
    }
}
