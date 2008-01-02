/*
 * Copyright 2005-2007 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package org.restlet.test.jaxrs.services.tests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.ws.rs.Path;

import junit.framework.TestCase;

import org.restlet.Application;
import org.restlet.Client;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Preference;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.ext.jaxrs.JaxRsRouter;

/**
 * This class allows easy testing of JAX-RS implementations by starting a server
 * for a given class and access the server for a given sub pass relativ to the
 * pass of the root resource class.
 * 
 * @author Stephan
 * 
 */
public abstract class JaxRsTestCase extends TestCase {
    public static final Protocol PROTOCOL = Protocol.HTTP;

    public static final int PORT = 8181;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        component = startServer(createRootResourceColl());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Component component;

    /**
     * @return
     */
    protected abstract Collection<Class<?>> createRootResourceColl();

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        stopServer(component);
    }

    /**
     * check, if the mainType and the subType is equal. The parameters are
     * ignored.
     * 
     * @param expected
     * @param actual
     */
    public static void assertEqualMediaType(MediaType expected, MediaType actual) {
        assertEquals(expected.getMainType(), actual.getMainType());
        assertEquals(expected.getSubType(), actual.getSubType());
    }

    /**
     * @param mediaType
     * @param mediaTypeQual
     *            default is 1.
     * @return
     */
    public static Collection<Preference<MediaType>> createPrefColl(
            MediaType mediaType, float mediaTypeQual) {
        if (mediaType == null)
            return Collections.EMPTY_LIST;
        return Collections.singleton(new Preference<MediaType>(mediaType,
                mediaTypeQual));
    }

    /**
     * @see #startServer(Protocol, int, Collection)
     */
    public static Component startServer(Class<?> klasse) throws Exception {
        return startServer(PROTOCOL, PORT, klasse);
    }

    /**
     * @see #startServer(Protocol, int, Collection)
     */
    public static Component startServer(Protocol protocol, int port,
            Class<?> rootResourceClass) throws Exception {
        final Collection<Class<?>> rootResourceClasses = new ArrayList();
        rootResourceClasses.add(rootResourceClass);
        return startServer(protocol, port, rootResourceClasses);
    }

    /**
     * @see #startServer(Protocol, int, Collection)
     */
    public static Component startServer(
            final Collection<Class<?>> rootResourceClasses) throws Exception {
        return startServer(PROTOCOL, PORT, rootResourceClasses);
    }

    /**
     * Starts the server with the given protocol on the given port with the given Collection of root resource classes.
     * The method {@link #setUp()} will do this on every test start up.
     * @param protocol
     * @param port
     * @param rootResourceClasses
     * @return Returns the started component. Should be stopped with {@link #stopServer(Component)}
     * @throws Exception
     */
    public static Component startServer(Protocol protocol, int port,
            final Collection<Class<?>> rootResourceClasses) throws Exception {
        Component comp = new Component();
        comp.getServers().add(protocol, port);

        // Create an application
        Application application = new Application(comp.getContext()) {
            @Override
            public Restlet createRoot() {
                JaxRsRouter router = new JaxRsRouter(getContext());
                Collection<Class<?>> rrcs = rootResourceClasses;
                for (Class cl : rrcs) {
                    router.attach(cl);
                }
                return router;
            }
        };

        // Attach the application to the component and start it
        comp.getDefaultHost().attach(application);
        comp.start();
        return comp;
    }

    /**
     * Stops the component. The method {@link #tearDown()} do this after every test.
     * @param component
     * @throws Exception
     */
    public static void stopServer(@SuppressWarnings("all")
    Component component) throws Exception {
        component.stop();
    }
    
    /**
     * @see #accessServer(Class, String, Method, Collection)
     */
    public static Response accessServer(Class klasse, Method httpMethod) {
        return accessServer(klasse, httpMethod, (Collection) null);
    }

    /**
     * @param klasse
     * @param httpMethod
     * @param mediaTypePrefs
     * @return
     */
    public static Response accessServer(Class klasse, Method httpMethod,
            MediaType mediaType) {
        return accessServer(klasse, httpMethod, createPrefColl(mediaType, 1f));
    }

    /**
     * @param klasse
     * @param httpMethod
     * @param mediaTypePrefs
     *            Collection with Preference&lt;MediaType&gt; and/or MediaType.
     * @return
     * @throws IllegalArgumentException
     *             If an element in the mediaTypes is neither a
     *             Preference&lt;MediaType&gt; or a MediaType-Objekten.
     */
    public static Response accessServer(Class klasse, Method httpMethod,
            Collection mediaTypes) throws IllegalArgumentException {
        return accessServer(klasse, null, httpMethod, mediaTypes);
    }

    /**
     * @param klasse
     * @param subPath
     * @param httpMethod
     * @param mediaTypes
     * @return
     */
    public static Response accessServer(Class klasse, String subPath,
            Method httpMethod, Collection mediaTypes) {
        Reference reference = createReference(klasse, subPath);
        Client client = new Client(PROTOCOL);
        Request request = new Request(httpMethod, reference);
        addAcceptedMediaTypes(request, mediaTypes);
        // ausgeben(request);
        Response response = client.handle(request);
        return response;
    }

    /**
     * @param request
     * @param mediaTypes
     */
    private static void addAcceptedMediaTypes(Request request,
            Collection mediaTypes) {
        if (mediaTypes != null && !mediaTypes.isEmpty()) {
            Collection<Preference<MediaType>> mediaTypePrefs = new ArrayList(
                    mediaTypes.size());
            for (Object mediaType : mediaTypes) {
                if (mediaType instanceof MediaType) {
                    mediaTypePrefs.add(new Preference<MediaType>(
                            (MediaType) mediaType));
                } else if (mediaType instanceof Preference) {
                    Preference preference = (Preference) mediaType;
                    if (preference.getMetadata() instanceof MediaType)
                        mediaTypePrefs.add(preference);
                } else {
                    throw new IllegalArgumentException(
                            "Valid mediaTypes are only Preference<MediaType> or MediaType");
                }
            }
            request.getClientInfo().getAcceptedMediaTypes().addAll(
                    mediaTypePrefs);
        }
    }

    /**
     * Creates an reference that access the localhost with the JaxRsTester
     * protocol and the JaxRsTester Port. It uses the path of the given
     * jaxRsClass
     * 
     * @param jaxRsClass
     * @param subPath
     *            darf null sein
     * @return
     */
    public static Reference createReference(Class jaxRsClass, String subPath) {
        Reference reference = new Reference();
        reference.setProtocol(PROTOCOL);
        reference.setAuthority("localhost");
        reference.setHostPort(PORT);
        String path = ((Path) jaxRsClass.getAnnotation(Path.class)).value();
        if (!path.startsWith("/"))
            path = "/" + path;
        if (subPath != null && subPath.length() > 0)
            path += "/" + subPath;
        reference.setPath(path);
        return reference;
    }
}