package org.factcast.server.rest.resources;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicReference;

import org.factcast.core.subscription.Subscription;
import org.factcast.server.rest.TestFacts;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercateo.common.rest.schemagen.JsonHyperSchema;
import com.mercateo.common.rest.schemagen.JsonHyperSchemaCreator;
import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.link.LinkMetaFactory;
import com.mercateo.common.rest.schemagen.link.relation.Rel;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchemaCreator;

@SuppressWarnings("all")
public class FactsObserver0Test {

    @Mock
    private EventOutput eventOutput;

    @SuppressWarnings("deprecation")
    @Spy
    private LinkFactory<FactsResource> linkFatory = LinkMetaFactory.createInsecureFactoryForTest()
            .createFactoryFor(FactsResource.class);

    private FactsObserver uut;

    @Mock
    private Subscription subscription;

    @Spy
    private FactTransformer factTransformer = new FactTransformer(new ObjectMapper());

    @Before
    public void setup() throws URISyntaxException {
        MockitoAnnotations.initMocks(this);
        AtomicReference<Subscription> subsup = new AtomicReference<Subscription>(subscription);
        URI baseURI = new URI("http://localhost:8080");
        HyperSchemaCreator hyperSchemaCreator = new HyperSchemaCreator(
                new ObjectWithSchemaCreator(), new JsonHyperSchemaCreator());
        uut = new FactsObserver(eventOutput, linkFatory, hyperSchemaCreator, baseURI, subsup,
                factTransformer, false);
    }

    @Test
    public void testOnNext() throws Exception {
        uut.onNext(TestFacts.one);
        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventOutput).write(cap.capture());
        OutboundEvent ev = cap.getValue();
        @SuppressWarnings("unchecked")
        JsonHyperSchema jsonHyperSchema = ((ObjectWithSchema<Void>) ev.getData()).schema;
        assertTrue(jsonHyperSchema.getByRel(Rel.CANONICAL).isPresent());
        assertThat(ev.getId(), is(TestFacts.one.id().toString()));
    }

    @Test
    public void testOnCatchup() throws Exception {
        uut.onCatchup();
        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventOutput).write(cap.capture());
        OutboundEvent event = cap.getValue();
        assertThat(event, is(notNullValue()));
        assertThat(event.getName(), is("catchup"));
        verifyNoMoreInteractions(subscription, linkFatory, eventOutput);

    }

    @Test
    public void testOnCatchupError() throws Exception {
        doThrow(IOException.class).when(eventOutput).write(any());

        uut.onCatchup();

        verify(subscription).close();
        verify(eventOutput).close();
        verify(eventOutput, times(1)).write(any());
        verifyNoMoreInteractions(subscription, linkFatory, eventOutput);

    }

    @Test
    public void testOnComplete() throws Exception {

        uut.onComplete();
        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventOutput).write(cap.capture());
        OutboundEvent event = cap.getValue();
        assertThat(event, is(notNullValue()));
        assertThat(event.getName(), is("complete"));
        verify(subscription).close();
        verify(eventOutput).close();
        verifyNoMoreInteractions(subscription, linkFatory, eventOutput);
    }

    @Test
    public void testCreationOfFulltype() throws Exception {

        ObjectWithSchema<FactJson> object = (ObjectWithSchema<FactJson>) uut.createPayload(
                TestFacts.one, true);
        assertThat(object.object.header().id(), is(TestFacts.one.id()));
    }

    @Test
    public void testCreationOfIdType() throws Exception {

        ObjectWithSchema<FactIdJson> object = (ObjectWithSchema<FactIdJson>) uut.createPayload(
                TestFacts.one, false);
        assertThat(object.object.id(), is(TestFacts.one.id().toString()));
    }

}
