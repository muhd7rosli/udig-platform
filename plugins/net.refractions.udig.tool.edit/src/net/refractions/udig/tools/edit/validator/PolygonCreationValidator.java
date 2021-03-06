/* uDig - User Friendly Desktop Internet GIS client
 * http://udig.refractions.net
 * (C) 2004, Refractions Research Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * (http://www.eclipse.org/legal/epl-v10.html), and the Refractions BSD
 * License v1.0 (http://udig.refractions.net/files/bsd3-v10.html).
 */
package net.refractions.udig.tools.edit.validator;

import net.refractions.udig.project.ui.render.displayAdapter.MapMouseEvent;
import net.refractions.udig.tool.edit.internal.Messages;
import net.refractions.udig.tools.edit.EditToolHandler;
import net.refractions.udig.tools.edit.EventType;
import net.refractions.udig.tools.edit.behaviour.IEditValidator;
import net.refractions.udig.tools.edit.support.EditUtils;
import net.refractions.udig.tools.edit.support.Point;
import net.refractions.udig.tools.edit.support.PrimitiveShape;

/**
 * Disallows:
 * <ul>
 * <li>Self intersections</li>
 * </ul>
 * @author Jesse
 * @since 1.1.0
 */
public class PolygonCreationValidator implements IEditValidator {

    public String isValid( EditToolHandler handler, MapMouseEvent event, EventType type ) {

        PrimitiveShape shell = handler.getCurrentShape();
        
        assert shell==shell.getEditGeom().getShell();
        
        Point newPoint = Point.valueOf(event.x, event.y);
        int lastPointIndex = shell.getNumPoints()-1;
        if( shell.getNumPoints()>2 && EditUtils.instance.intersection(shell.getPoint(lastPointIndex), newPoint, shell, 0, lastPointIndex) ){
            return Messages.ValidHoleValidator_selfIntersection;
        }
        return null;
    }

}
