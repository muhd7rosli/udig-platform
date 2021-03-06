/* uDig - User Friendly Desktop Internet GIS client
 * http://udig.refractions.net
 * (C) 2004-2012, Refractions Research Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * (http://www.eclipse.org/legal/epl-v10.html), and the Refractions BSD
 * License v1.0 (http://udig.refractions.net/files/bsd3-v10.html).
 */
package net.refractions.udig.project.internal.commands.selection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import net.refractions.udig.project.command.AbstractCommand;
import net.refractions.udig.project.command.UndoableMapCommand;
import net.refractions.udig.project.internal.Layer;
import net.refractions.udig.project.internal.Messages;

import org.eclipse.core.runtime.IProgressMonitor;
import org.opengis.filter.Filter;

/**
 * A command that removes all selection.
 * 
 * @author jeichar
 * @since 0.2
 */
public class NoSelectCommand extends AbstractCommand implements UndoableMapCommand {

    private Map<Layer, Filter> undoState=new HashMap<Layer, Filter>();
    /**
     * @see net.refractions.udig.project.internal.command.MapCommand#run()
     */
    public void run( IProgressMonitor monitor ) {
        List<Layer> layers = getMap().getLayersInternal();
        for( Layer layer : layers ) {
            undoState.put(layer, layer.getFilter());
        }
        getMap().select(Filter.EXCLUDE);
    }

    /**
     * @see net.refractions.udig.project.command.UndoableCommand#rollback()
     */
    public void rollback( IProgressMonitor monitor ) throws Exception {
        Set<Entry<Layer, Filter>> state = undoState.entrySet();
        for( Entry<Layer, Filter> entry : state ) {
            entry.getKey().setFilter(entry.getValue());
        }
    }

    /**
     * @see net.refractions.udig.project.command.MapCommand#getName()
     */
    public String getName() {
        return Messages.NoSelectCommand_cancelSelections; 
    }

}