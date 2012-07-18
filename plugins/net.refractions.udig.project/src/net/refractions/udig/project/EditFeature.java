/*
 *    uDig - User Friendly Desktop Internet GIS client
 *    http://udig.refractions.net
 *    (C) 2004, Refractions Research Inc.
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 */
package net.refractions.udig.project;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.refractions.udig.project.EditFeature.AttributeStatus;
import net.refractions.udig.project.internal.commands.edit.SetAttributeCommand;
import net.refractions.udig.project.internal.commands.edit.SetAttributesCommand;
import net.refractions.udig.project.listener.EditFeatureListenerList;
import net.refractions.udig.project.listener.EditFeatureListener;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Platform;
import org.geotools.feature.DecoratingFeature;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.IllegalAttributeException;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;

import com.sun.org.apache.xpath.internal.operations.Bool;
import com.vividsolutions.jts.geom.Geometry;

/**
 * A SimpleFeature can handle setAttribute in a threadsafe way.
 * 
 * @since 1.2.0
 */
public class EditFeature extends DecoratingFeature implements IAdaptable, SimpleFeature {
    private IEditManager manager;

    private EditFeatureListenerList editFeatureListeners = new EditFeatureListenerList();

    // not used yet; could be used to "batch up" changes to send in one command?
    private Set<String> dirty = new LinkedHashSet<String>(); // we no longer need this

    private Map<Name, AttributeStatus> attribureStatusList = new HashMap<Name, AttributeStatus>();

    private boolean batch;

    /**
     * a data object that holds the status if a attribute.
     * 
     * @author leviputna
     * 
     */
    class AttributeStatus {
        private Boolean dirty = false;

        private Boolean visible = true;

        private Boolean enabled = true;

        private Boolean editable = true;

        /**
         * Check if the attribute value has changes in the EditFeature but has not been updated in
         * the feature model.
         * 
         * @return true if the attribute value has changes in the EditFeature but not in the Feature
         */
        public Boolean getDirty() {
            return dirty;
        }

        /**
         * Mark this attributer as changes so that EditManager can save the commit changes to the
         * feature model
         * 
         * @param dirty the dirty status to set
         */
        public void setDirty(Boolean dirty) {
            this.dirty = dirty;
        }

        /**
         * Returns <code>true</code> if the receiver is visible, and <code>false</code> otherwise.
         * 
         * @return the receiver's visibility state
         */
        public Boolean getVisible() {
            return visible;
        }

        /**
         * Set weather or not this attribute should be shown.
         * 
         * @param the visibility to set.
         */
        public void setVisible(Boolean visible) {
            this.visible = visible;
        }

        /**
         * Returns <code>true</code> if the attributes is enabled, and <code>false</code> otherwise.
         * When used in a control enabled false is typically not selectable from the user interface
         * and draws with an inactive or "grayed" look.
         * 
         * @return the receiver's enabled state
         */
        public Boolean getEnabled() {
            return enabled;
        }

        /**
         * Returns <code>true</code> if the attributes is disabled, and <code>false</code>
         * otherwise. When used in a control disabled false is typically not selectable from the
         * user interface and draws with an inactive or "grayed" look.
         * 
         * <p>
         * This is a convenience method and is equivalent to <code>!getEnabled();</code>
         * </p>
         * 
         * @return the attributes disabled state
         */
        public Boolean getDisabled() {
            return !enabled;
        }

        /**
         * Enables the receiver if the argument is <code>true</code>, and disables it otherwise.
         * 
         * @param enabled
         */
        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Check is this attribute is editable. Returns <code>true</code> if the attributes is
         * editable, and <code>false</code> otherwise.
         * 
         * @return the attributes editable state
         */
        public Boolean getEditable() {
            return editable;
        }

        /**
         * Set the editability of this attribute
         * 
         * @param true to make this attribute editable, false to make it read only.
         */
        public void setEditable(Boolean editable) {
            this.editable = editable;
        }

    }

    /**
     * Construct <code>AdaptableFeature</code>.
     * 
     * @param feature the wrapped feature
     * @param evaluationObject the layer that contains the feature.
     */
    public EditFeature(IEditManager manager) {
        super(manager.getEditFeature());
        this.manager = manager;
    }

    public EditFeature(IEditManager manager, SimpleFeature feature) {
        super(feature);
        this.manager = manager;
    }

    /**
     * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
     */
    @SuppressWarnings("unchecked")
    public Object getAdapter(Class adapter) {
        if (IEditManager.class.isAssignableFrom(adapter)) {
            if (manager != null) {
                return manager;
            }
        }
        if (ILayer.class.isAssignableFrom(adapter)) {
            if (manager != null) {
                return manager.getEditLayer();
            }
        }
        return Platform.getAdapterManager().getAdapter(this, adapter);
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public void setAttribute(int index, Object value) {
        SimpleFeatureType schema = getFeatureType();
        AttributeDescriptor attribute = schema.getAttributeDescriptors().get(index);

        Object oldValue = getAttribute(index);
        String name = attribute.getLocalName();
        SetAttributeCommand sync = new SetAttributeCommand(name, value);
        dirty.add(name);
        manager.getMap().sendCommandASync(sync);
        doValueChange(attribute.getLocalName(), oldValue, value);
    }

    @Override
    public void setAttribute(Name name, Object value) {
        Object oldValue = getAttribute(name);// needs to get the value before we run
                                             // SetAttributeCommand

        SetAttributeCommand sync = new SetAttributeCommand(name.getLocalPart(), value);
        dirty.add(name.getLocalPart());
        manager.getMap().sendCommandASync(sync);
        doValueChange(name.getLocalPart(), oldValue, value);
    }

    @Override
    public void setAttribute(String path, Object value) {
        Object oldValue = getAttribute(path);
        // System.out.println("made it to set attribute");
        SetAttributeCommand sync = new SetAttributeCommand(path, value);
        // System.out.println("made it to before dirty");
        dirty.add(path);
        // System.out.println("made it to after dirty");
        manager.getMap().sendCommandASync(sync);
        doValueChange(path, oldValue, value);
    }

    @Override
    public void setAttributes(List<Object> values) {
        String[] xpath;
        Object[] value;
        ArrayList<String> xpathlist = new ArrayList<String>();
        SimpleFeatureType schema = getFeatureType();
        for (PropertyDescriptor x : schema.getDescriptors()) {
            xpathlist.add(x.getName().getLocalPart());
            dirty.add(x.getName().getLocalPart());
        }
        xpath = xpathlist.toArray(new String[xpathlist.size()]);
        value = values.toArray();
        SetAttributesCommand sync = new SetAttributesCommand(xpath, value);
        manager.getMap().sendCommandASync(sync);
    }

    @Override
    public void setAttributes(Object[] values) {
        String[] xpath;
        ArrayList<String> xpathlist = new ArrayList<String>();
        SimpleFeatureType schema = getFeatureType();
        for (PropertyDescriptor x : schema.getDescriptors()) {
            xpathlist.add(x.getName().getLocalPart());
        }
        dirty.addAll(xpathlist);
        xpath = xpathlist.toArray(new String[xpathlist.size()]);
        SetAttributesCommand sync = new SetAttributesCommand(xpath, values);
        manager.getMap().sendCommandASync(sync);
    }

    @Override
    // This is simply the same as in DecoratingFeature.class
    public void setDefaultGeometry(Object geometry) {
        GeometryDescriptor geometryDescriptor = getFeatureType().getGeometryDescriptor();
        setAttribute(geometryDescriptor.getName(), geometry);

        Object oldValue = getAttribute(geometryDescriptor.getName());
        doValueChange(geometryDescriptor.getLocalName(), oldValue, geometry);
    }

    @Override
    public void setDefaultGeometryProperty(GeometryAttribute geometryAttribute) {
        if (geometryAttribute != null)
            setDefaultGeometry(geometryAttribute.getValue());
        else
            setDefaultGeometry(null);
    }

    @Override
    public void setValue(Collection<Property> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setValue(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDefaultGeometry(Geometry geometry) throws IllegalAttributeException {
        GeometryDescriptor geometryDescriptor = getFeatureType().getGeometryDescriptor();
        setAttribute(geometryDescriptor.getName(), geometry);
    }

    public void setBatch(boolean batch) {
        this.batch = batch;
    }

    /**
     * get the status of an attribute {@link AttributeStatus} so status options.
     * 
     * <pre>
     * SimpleFeatureType schema = getFeatureType();
     * 
     * AttributeDescriptor attribute = schema.getAttributeDescriptors().get(index);
     * 
     * Name attributeName = attribute.getLocalName();
     * 
     * // Get the viability status of an attribute.
     * Boolean attributeVisable = editFeature.getStatus(attributeName).getVisable();
     * 
     * // Set the viability status of an attribute.
     * editFeature.getStatus(attributeName).setEditable(false);
     * </pre>
     * 
     * @param attribute
     * @return
     */
    public AttributeStatus getState(Name attribute) {

        if (attribureStatusList.containsKey(attribute)) {
            return attribureStatusList.get(attribute);
        } else {
            AttributeStatus status = new AttributeStatus();
            attribureStatusList.put(attribute, status);
            return status;
        }
    }

    /**
     * add a listener to this EditFeature. This method has no effect if the <a
     * href="ListenerList.html#same">same</a> listener is already registered.
     * 
     * @param listener the non-<code>null</code> listener to add
     */
    public void addEditFeatureListener(EditFeatureListener listener) {
        editFeatureListeners.add(listener);
    }

    /**
     * Removes a listener from this EditFeature. Has no effect if the <a
     * href="ListenerList.html#same">same</a> listener was not already registered.
     * 
     * @param listener the non-<code>null</code> listener to remove
     */
    public void removeEditFeatureListener(EditFeatureListener listener) {
        editFeatureListeners.remove(listener);
    }

    private void doValueChange(String attributeName, Object oldValue, Object newValue) {
        editFeatureListeners.doValueChange(new PropertyChangeEvent(this, attributeName, oldValue,
                newValue));
    }
}