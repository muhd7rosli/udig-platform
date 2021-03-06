/*
 *    uDig - User Friendly Desktop Internet GIS client
 *    http://udig.refractions.net
 *    (C) 2012, Refractions Research Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * (http://www.eclipse.org/legal/epl-v10.html), and the Refractions BSD
 * License v1.0 (http://udig.refractions.net/files/bsd3-v10.html).
 */
package net.refractions.udig.catalog.tests.internal.wfs;

import java.net.URL;

import net.refractions.udig.catalog.IService;
import net.refractions.udig.catalog.internal.wfs.WFSServiceExtension;
import net.refractions.udig.catalog.tests.AbstractServiceTest;

import org.junit.Before;

/**
 * @author dzwiers
 */
public class WFSServiceTest extends AbstractServiceTest {

    private IService service = null;
    
    @Before
    public void setUp() throws Exception {
        WFSServiceExtension fac = new WFSServiceExtension();
        URL url = new URL("http://demo.opengeo.org/geoserver/wfs?"); //$NON-NLS-1$
        service = fac.createService(url, fac.createParams(url));
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see net.refractions.udig.catalog.tests.AbstractServiceTest#getResolve()
     */
    protected IService getResolve() {
        return service;
    }

    /*
     * (non-Javadoc)
     * 
     * @see net.refractions.udig.catalog.tests.AbstractResolveTest#hasParent()
     */
    protected boolean hasParent() {
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see net.refractions.udig.catalog.tests.AbstractResolveTest#isLeaf()
     */
    protected boolean isLeaf() {
        return false;
    }

}
