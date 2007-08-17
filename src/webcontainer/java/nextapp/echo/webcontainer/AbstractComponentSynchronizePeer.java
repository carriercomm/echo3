/* 
 * This file is part of the Echo Web Application Framework (hereinafter "Echo").
 * Copyright (C) 2002-2007 NextApp, Inc.
 *
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 */

package nextapp.echo.webcontainer;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import nextapp.echo.app.Component;
import nextapp.echo.app.reflect.ComponentIntrospector;
import nextapp.echo.app.reflect.IntrospectorFactory;
import nextapp.echo.app.update.ClientUpdateManager;
import nextapp.echo.app.update.ServerComponentUpdate;
import nextapp.echo.app.util.Context;

/**
 * Default abstract implementation of <code>ComponentSynchronizePeer</code>.
 * Provides implementations of all methods less <code>getComponentClass()</code>.
 * Determines properties to render to client by quertying a <code>Component</code>'s
 * local style and using a <code>ComponentIntrospector</code> to determine whether
 * those properties 
 */
public abstract class AbstractComponentSynchronizePeer 
implements ComponentSynchronizePeer {

    /**
     * Peer for synchronizing events between client and server.
     * This is a convenience object that is used with the
     * <code>addEvent()</code> method of the <code>AbstractComponentSynchronizePeer</code>
     * object.
     * 
     *  This object will often be derived with overriding implementations of the
     *  <code>hasListeners()</code> method to return true in cases where the supported
     *  server-side <code>Component</code> has registered listeners of the appropriate type,
     *  such that only events that actually will result in code being executed will cause
     *  immediate server interactions.
     */
    public static class EventPeer {
        
        private Class eventDataClass;
        
        private String eventName;
        
        private String listenerPropertyName;
        
        public EventPeer() {
            this(null, null, null);
        }
        
        public EventPeer(String eventName, String listenerPropertyName) {
            this(eventName, listenerPropertyName, null);
        }
        
        public EventPeer(String eventName, String listenerPropertyName, Class eventDataClass) {
            super();
            this.eventName = eventName;
            this.listenerPropertyName = listenerPropertyName;
            this.eventDataClass = eventDataClass;
        }
        
        public String getEventName() {
            return eventName;
        }
        
        public String getListenerPropertyName() {
            return listenerPropertyName;
        }
        
        public Class getEventDataClass() {
            return eventDataClass;
        }
        
        public boolean hasListeners(Context context, Component c) {
            return true;
        }

        public void processEvent(Context context, Component component, Object eventData) {
            ClientUpdateManager clientUpdateManager = (ClientUpdateManager) context.get(ClientUpdateManager.class);
            clientUpdateManager.setComponentAction(component, eventName, eventData);
        }
    }
    
    /**
     * A <code>Set</code> containing the names of all additional properties to be
     * rendered to the client.
     */
    private Set additionalProperties = null;
    
    /**
     * A <code>Set</code> containing the names of all style properties.
     */
    private Set stylePropertyNames = null;
    
    /**
     * A <code>Set</code> containing the names of all properties which are indexed.
     */
    private Set indexedPropertyNames = null;
    
    
    private Set referencedProperties = null;
    
    /**
     * The determined client component type.
     * 
     * @see #getClientComponentType()
     */
    private String clientComponentType;

    private Map eventTypeToEventPeer;
    
    /**
     * Default constructor.
     */
    public AbstractComponentSynchronizePeer() {
        super();
        clientComponentType = getComponentClass().getName();
        if (clientComponentType.startsWith("nextapp.echo.app.")) {
            // Use relative class name automatically for nextapp.echo.app objects.
            int lastDot = clientComponentType.lastIndexOf(".");
            clientComponentType = clientComponentType.substring(lastDot + 1);
        }
        
        try {
            stylePropertyNames = new HashSet();
            indexedPropertyNames = new HashSet();
            Class componentClass = getComponentClass();
            ComponentIntrospector ci = (ComponentIntrospector) IntrospectorFactory.get(componentClass.getName(),
                    componentClass.getClassLoader());
            Iterator propertyNameIt = ci.getPropertyNames();
            while (propertyNameIt.hasNext()) {
                String propertyName = (String) propertyNameIt.next();
                if (ci.getStyleConstantName(propertyName) != null) {
                    stylePropertyNames.add(propertyName);
                    if (ci.isIndexedProperty(propertyName)) {
                        indexedPropertyNames.add(propertyName);
                    }
                }
            }
        } catch (ClassNotFoundException ex) {
            // Should never occur.
            throw new RuntimeException("Internal error.", ex);
        }
    }
    
    public void addEvent(EventPeer eventPeer) {
        if (eventTypeToEventPeer == null) {
            eventTypeToEventPeer = new HashMap();
        }
        eventTypeToEventPeer.put(eventPeer.getEventName(), eventPeer);
    }
    
    /**
     * Adds a non-indexed output property.  
     * 
     * @see #addOutputProperty(java.lang.String, boolean)
     */
    public void addOutputProperty(String propertyName) {
        addOutputProperty(propertyName, false);
    }

    /**
     * Adds an output property.  
     * Property names added via this method will be returned by the 
     * <code>getOutputPropertyName()</code> method of this class.
     * If the indexed flag is set, the <code>isOutputPropertyIndexed</code>
     * method will also return true for this property name
     * 
     * @param propertyName the property name to add
     * @param indexed a flag indicating whether the property is indexed
     */
    public void addOutputProperty(String propertyName, boolean indexed) {
        if (additionalProperties == null) {
            additionalProperties = new HashSet();
        }
        additionalProperties.add(propertyName);
        if (indexed) {
            indexedPropertyNames.add(propertyName);
        }
    }

    /**
     * Default implementation: return full class name if component is not in core Echo package.
     * Return relative name for base Echo classes.
     * Overriding this method is not generally recommended, due to potential client namespace issues.
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#getClientComponentType()
     */
    public String getClientComponentType() {
        return clientComponentType;
    }
    
    /**
     * Returns the (most basic) supported component class.
     * 
     * @return the (most basic) supported component class
     */
    public abstract Class getComponentClass();
    
    /**
     * Returns null.  Implementations should override if they wish
     * to provide event data.
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#getEventDataClass(java.lang.String)
     */
    public Class getEventDataClass(String eventType) {
        if (eventTypeToEventPeer == null) {
            return null;
        }
        EventPeer eventPeer = (EventPeer) eventTypeToEventPeer.get(eventType);
        if (eventPeer == null) {
            return null;
        }
        return eventPeer.getEventDataClass();
    }

    /**
     * Returns an iterator containing all event types registered using <code>addEvent()</code>.
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#getEventTypes(Context, Component)
     */
    public Iterator getEventTypes(Context context, Component component) {
        if (eventTypeToEventPeer == null) {
            return Collections.EMPTY_SET.iterator();
        } else {
            return Collections.unmodifiableSet(eventTypeToEventPeer.keySet()).iterator();
        }
    }
    
    /**
     * Returns any property from the local style of the <code>Component</code>.
     * Implementations should override if they wish to support additional properties.
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#getOutputProperty(nextapp.echo.app.util.Context,
     *      nextapp.echo.app.Component, java.lang.String, int)
     */
    public Object getOutputProperty(Context context, Component component, String propertyName, int propertyIndex) {
        if (propertyIndex == -1) {
            return component.getLocalStyle().getProperty(propertyName);
        } else {
            return component.getLocalStyle().getIndexedProperty(propertyName, propertyIndex);
        }
    }
    
    /**
     * Returns the indices of any indexed property from the local style of the <code>Component</code>.
     * Implementations should override if they wish to support additional properties.
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#getOutputPropertyIndices(nextapp.echo.app.util.Context, 
     *      nextapp.echo.app.Component, java.lang.String)
     */
    public Iterator getOutputPropertyIndices(Context context, Component component, String propertyName) {
        return component.getLocalStyle().getPropertyIndices(propertyName);
    }
    
    /**
     * Returns null.
     * Implementations should override if they wish to set properties on the client by invoking 
     * specific methods other than setProperty()/setIndexedProperty().
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#getOutputPropertyMethodName(
     *      nextapp.echo.app.util.Context, nextapp.echo.app.Component, java.lang.String)
     */
    public String getOutputPropertyMethodName(Context context, Component component, String propertyName) {
        return null;
    }

    /**
     * Returns the names of all properties currently set in the component's local <code>Style</code>,
     * in addition to any properties added by invoking <code>addOutputProperty()</code>.
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#getOutputPropertyNames(Context, nextapp.echo.app.Component)
     */
    public Iterator getOutputPropertyNames(Context context, Component component) {
        final Iterator styleIterator = component.getLocalStyle().getPropertyNames();
        final Iterator additionalPropertyIterator 
                = additionalProperties == null ? null : additionalProperties.iterator();
        
        return new Iterator() {
        
            /**
             * @see java.util.Iterator#hasNext()
             */
            public boolean hasNext() {
                return styleIterator.hasNext() || 
                        (additionalPropertyIterator != null && additionalPropertyIterator.hasNext());
            }
        
            /**
             * @see java.util.Iterator#next()
             */
            public Object next() {
                if (styleIterator.hasNext()) {
                    return styleIterator.next();
                } else {
                    return additionalPropertyIterator.next(); 
                }
            }
        
            /**
             * @see java.util.Iterator#remove()
             */
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
   
    /**
     * Returns null.  Implementations receiving input properties should override.
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#getInputPropertyClass(java.lang.String)
     */
    public Class getInputPropertyClass(String propertyName) {
        return null;
    }

    /**
     * Returns property names that have been updated in the specified 
     * <code>ServerComponentUpdate</code> that are either part of the local style
     * or have been added via the <code>addOutputProperty()</code> method.
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#getUpdatedOutputPropertyNames(nextapp.echo.app.util.Context,
     *      nextapp.echo.app.Component,
     *      nextapp.echo.app.update.ServerComponentUpdate)
     */
    public Iterator getUpdatedOutputPropertyNames(Context context, Component component, 
            ServerComponentUpdate update) {
        if (!update.hasUpdatedProperties()) {
            return Collections.EMPTY_SET.iterator();
        }

        String[] updatedPropertyNames = update.getUpdatedPropertyNames();
        Set propertyNames = new HashSet();
        //FIXME. not particularly efficient.
        for (int i = 0; i < updatedPropertyNames.length; ++i) {
            if (stylePropertyNames.contains(updatedPropertyNames[i])
                    || (additionalProperties != null && additionalProperties.contains(updatedPropertyNames[i]))) {
                propertyNames.add(updatedPropertyNames[i]);
            }
        }
        return propertyNames.iterator();
    }

    /**
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#hasListeners(nextapp.echo.app.util.Context, 
     *      nextapp.echo.app.Component, java.lang.String)
     */
    public boolean hasListeners(Context context, Component component, String eventType) {
        if (eventTypeToEventPeer == null) {
            return false;
        }
        EventPeer eventPeer = (EventPeer) eventTypeToEventPeer.get(eventType);
        if (eventPeer == null) {
            return false;
        }
        return eventPeer.hasListeners(context, component);
    }

    /**
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#hasUpdatedListeners(nextapp.echo.app.util.Context, 
     *      nextapp.echo.app.Component, nextapp.echo.app.update.ServerComponentUpdate, java.lang.String)
     */
    public boolean hasUpdatedListeners(Context context, Component component, ServerComponentUpdate update, 
            String eventType) {
        if (eventTypeToEventPeer == null) {
            return false;
        }
        EventPeer eventPeer = (EventPeer) eventTypeToEventPeer.get(eventType);
        if (eventPeer == null) {
            return false;
        }
        return update.hasUpdatedProperty(eventPeer.getListenerPropertyName());
    }

    /**
     * Does nothing.  Implementations requiring initialization should override this method.
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#init(Context)
     */
    public void init(Context context) {
        // Do nothing.
    }

    /**
     * Determines if a local style property or additional property (added via <code>addOutputProperty()</code>)
     * is indexed.
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#isOutputPropertyIndexed(nextapp.echo.app.util.Context, 
     *      nextapp.echo.app.Component, java.lang.String)
     */
    public boolean isOutputPropertyIndexed(Context context, Component component, String propertyName) {
        return indexedPropertyNames.contains(propertyName);
    }

    /**
     * Returns true for any property set as rendered-by-reference via the
     * <code>setOutputPropertyReferenced()</code> method.
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#isOutputPropertyReferenced(
     *      nextapp.echo.app.util.Context, nextapp.echo.app.Component, java.lang.String)
     */
    public boolean isOutputPropertyReferenced(Context context, Component component, String propertyName) {
        return referencedProperties != null && referencedProperties.contains(propertyName);
    }
        
    /**
     * Does nothing.  Implementations handling events should overwrite this method.
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#processEvent(nextapp.echo.app.util.Context,
     *      nextapp.echo.app.Component, java.lang.String, java.lang.Object)
     */
    public void processEvent(Context context, Component component, String eventType, Object eventData) {
        if (eventTypeToEventPeer == null) {
            return;
        }
        EventPeer eventPeer = (EventPeer) eventTypeToEventPeer.get(eventType);
        if (eventPeer == null) {
            return;
        }
        eventPeer.processEvent(context, component, eventData);
    }

    /**
     * Sets the rendered-by-reference state of a property.
     * <code>isOutputPropertyReferenced</code> will return true for any property set as
     * referenced using this method.
     * 
     * @param propertyName the propertyName
     * @param newValue true if the property should be rendered by reference
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#isOutputPropertyReferenced(
     *      nextapp.echo.app.util.Context, nextapp.echo.app.Component, java.lang.String)
     */
    public void setOutputPropertyReferenced(String propertyName, boolean newValue) {
        if (newValue) {
            if (referencedProperties == null) {
                referencedProperties = new HashSet();
            }
            referencedProperties.add(propertyName);
        } else {
            if (referencedProperties != null) {
                referencedProperties.remove(propertyName);
            }
        }
    }
    
    /**
     * Does nothing.  Implementations that receive input from the client should override this method.
     * 
     * @see nextapp.echo.webcontainer.ComponentSynchronizePeer#storeInputProperty(nextapp.echo.app.util.Context, 
     *      nextapp.echo.app.Component, java.lang.String, int, java.lang.Object)
     */
    public void storeInputProperty(Context context, Component component, String propertyName, int index, Object newValue) {
        // Do nothing.
    }
}
