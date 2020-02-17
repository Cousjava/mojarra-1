/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.faces.facelets.tag;

import com.sun.faces.el.ELUtils;
import com.sun.faces.facelets.el.ContextualCompositeMethodExpression;
import com.sun.faces.facelets.el.ELText;
import com.sun.faces.facelets.el.TagMethodExpression;
import com.sun.faces.facelets.el.TagValueExpression;
import com.sun.faces.facelets.el.ContextualCompositeValueExpression;
import com.sun.faces.util.MessageUtils;
import static com.sun.faces.util.MessageUtils.ARGUMENTS_NOT_LEGAL_CC_ATTRS_EXPR;
import com.sun.faces.util.Util;

import javax.el.ELException;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.ValueExpression;
import javax.el.MethodInfo;
import javax.el.ELContext;
import javax.faces.view.Location;
import javax.faces.view.facelets.FaceletContext;
import javax.faces.view.facelets.Tag;
import javax.faces.view.facelets.TagAttribute;
import javax.faces.view.facelets.TagAttributeException;
import javax.faces.FacesException;

/**
 * Representation of a Tag's attribute in a Facelet File
 * 
 * @author Jacob Hookom
 * @version $Id$
 */
public class TagAttributeImpl extends TagAttribute {

    private final boolean literal;

    private Boolean hasCompositeComponentExpr;
    private Boolean hasCompositeComponentLookupWithArgs;
    private Boolean hasCompositeComponentMethodExprLookup;

    private final String localName;

    private final Location location;

    private final String namespace;

    private final String qName;

    private final String value;

    private String string;
    
    private Tag tag;
    
    public TagAttributeImpl() {
        this.literal = false;
        this.localName = null;
        this.location = null;
        this.namespace = null;
        this.qName = null;
        this.value = null;
        this.string = null;
        this.tag = null;
    } 

    public TagAttributeImpl(Location location, String ns, String localName,
            String qName, String value) {
        this.location = location;
        this.namespace = ns;
        this.localName = (null == localName || 0 == localName.length()) ? qName : localName;
        this.qName = qName;
        this.value = value;
        try {
            this.literal = ELText.isLiteral(this.value);
        } catch (ELException e) {
            throw new TagAttributeException(this, e);
        }
        
    }

    /**
     * <p class="changed_modified_2_3">
     * If literal,return
     * {@link Boolean#valueOf(java.lang.String)  Boolean.valueOf(java.lang.String)}
     * passing our value, otherwise call
     * {@link #getObject(FaceletContext, Class) getObject(FaceletContext, Class)}.
     * </p>
     * 
     * @see Boolean#valueOf(java.lang.String) 
     * @see #getObject(FaceletContext, Class)
     * @param ctx FaceletContext to use
     * @return boolean value
     */
    @Override
    public boolean getBoolean(FaceletContext ctx) {
        if (this.literal) {
            return Boolean.valueOf(this.value);
        } else {
            Boolean bool = (Boolean) this.getObject(ctx, Boolean.class);
            if (bool == null) {
                bool = false;
            }
            return bool;
        }
    }

    /**
     * If literal, call
     * {@link Integer#parseInt(java.lang.String) Integer.parseInt(String)},
     * otherwise call
     * {@link #getObject(FaceletContext, Class) getObject(FaceletContext, Class)}.
     * 
     * @see Integer#parseInt(java.lang.String)
     * @see #getObject(FaceletContext, Class)
     * @param ctx
     *            FaceletContext to use
     * @return int value
     */
    @Override
    public int getInt(FaceletContext ctx) {
        if (this.literal) {
            return Integer.parseInt(this.value);
        } else {
            return ((Number) this.getObject(ctx, Integer.class)).intValue();
        }
    }

    /**
     * Local name of this attribute
     * 
     * @return local name of this attribute
     */
    @Override
    public String getLocalName() {
        return this.localName;
    }

    /**
     * The location of this attribute in the FaceletContext
     * 
     * @return the TagAttributeImpl's location
     */
    @Override
    public Location getLocation() {
        return this.location;
    }

    /**
     * Create a MethodExpression, using this attribute's value as the expression
     * String.
     * 
     * @see ExpressionFactory#createMethodExpression(javax.el.ELContext,
     *      java.lang.String, java.lang.Class, java.lang.Class[])
     * @see MethodExpression
     * @param ctx
     *            FaceletContext to use
     * @param type
     *            expected return type
     * @param paramTypes
     *            parameter type
     * @return a MethodExpression instance
     */
    @Override
    public MethodExpression getMethodExpression(FaceletContext ctx,
                                                Class type,
                                                Class[] paramTypes) {

        MethodExpression result;

        try {
            ExpressionFactory f = ctx.getExpressionFactory();
            if (hasCompositeComponentLookupWithArgs()) {
                String message =
                      MessageUtils.getExceptionMessageString(ARGUMENTS_NOT_LEGAL_CC_ATTRS_EXPR);
                throw new TagAttributeException(this, message);
            }
            // Determine if this is a composite component attribute lookup.
            // If so, look for a MethodExpression under the attribute key
            if (hasCompositeComponentMethodExprLookup()) {
                result = new AttributeLookupMethodExpression(getValueExpression(ctx, MethodExpression.class));
            } else if (hasCompositeComponentExpr()) {
                MethodExpression delegate = new TagMethodExpression(this,
                                                 f.createMethodExpression(ctx,
                                                                          this.value,
                                                                          type,
                                                                          paramTypes));
                result = new ContextualCompositeMethodExpression(getLocation(), delegate);
            } else {
                result = new TagMethodExpression(this,
                                                 f.createMethodExpression(ctx,
                                                                          this.value,
                                                                          type,
                                                                          paramTypes));
            }
        } catch (TagAttributeException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TagAttributeException(this, e);
        }
        return result;
    }

    /**
     * The resolved Namespace for this attribute
     * 
     * @return resolved Namespace
     */
    @Override
    public String getNamespace() {
        return this.namespace;
    }

    /**
     * Delegates to getObject with Object.class as a param
     * 
     * @see #getObject(FaceletContext, Class)
     * @param ctx
     *            FaceletContext to use
     * @return Object representation of this attribute's value
     */
    @Override
    public Object getObject(FaceletContext ctx) {
        return this.getObject(ctx, Object.class);
    }

    /**
     * The qualified name for this attribute
     * 
     * @return the qualified name for this attribute
     */
    @Override
    public String getQName() {
        return this.qName;
    }

    @Override
    public Tag getTag() {
        return this.tag;
    }
    
    @Override
    public void setTag(Tag tag) {
        this.tag = tag;
    }

    /**
     * Return the literal value of this attribute
     * 
     * @return literal value
     */
    @Override
    public String getValue() {
        return this.value;
    }

    /**
     * If literal, then return our value, otherwise delegate to getObject,
     * passing String.class.
     * 
     * @see #getObject(FaceletContext, Class)
     * @param ctx
     *            FaceletContext to use
     * @return String value of this attribute
     */
    @Override
    public String getValue(FaceletContext ctx) {
        if (this.literal) {
            return this.value;
        } else {
            return (String) this.getObject(ctx, String.class);
        }
    }

    /**
     * If literal, simply coerce our String literal value using an
     * ExpressionFactory, otherwise create a ValueExpression and evaluate it.
     * 
     * @see ExpressionFactory#coerceToType(java.lang.Object, java.lang.Class)
     * @see ExpressionFactory#createValueExpression(javax.el.ELContext,
     *      java.lang.String, java.lang.Class)
     * @see ValueExpression
     * @param ctx
     *            FaceletContext to use
     * @param type
     *            expected return type
     * @return Object value of this attribute
     */
    @Override
    public Object getObject(FaceletContext ctx, Class type) {
        if (this.literal) {
            if (String.class.equals(type)) {
                return this.value;
            } else {
                try {
                    return ctx.getExpressionFactory().coerceToType(this.value,
                            type);
                } catch (Exception e) {
                    throw new TagAttributeException(this, e);
                }
            }
        } else {
            ValueExpression ve = this.getValueExpression(ctx, type);
            try {
                return ve.getValue(ctx);
            } catch (Exception e) {
                throw new TagAttributeException(this, e);
            }
        }
    }

    /**
     * Create a ValueExpression, using this attribute's literal value and the
     * passed expected type.
     * 
     * @see ExpressionFactory#createValueExpression(javax.el.ELContext,
     *      java.lang.String, java.lang.Class)
     * @see ValueExpression
     * @param ctx
     *            FaceletContext to use
     * @param type
     *            expected return type
     * @return ValueExpression instance
     */
    @Override
    public ValueExpression getValueExpression(FaceletContext ctx, Class type) {
        return getValueExpression(ctx, this.value, type);
    }

    /**
     * If this TagAttributeImpl is literal (not #{..} or ${..})
     * 
     * @return true if this attribute is literal
     */
    @Override
    public boolean isLiteral() {
        return this.literal;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        if (this.string == null) {
            this.string = this.location + " " + this.qName + "=\"" + this.value
                    + "\"";
        }
        return this.string;
    }


    // --------------------------------------------------------- Private Methods

    // this should have been private but can't be changed without breaking existing code
    public ValueExpression getValueExpression(FaceletContext ctx, String expr, Class type) {
        boolean eq = this.value != null && this.value.equals(expr);
        try {
            if ((eq && hasCompositeComponentLookupWithArgs()) || (!eq && ELUtils.isCompositeComponentLookupWithArgs(expr))) {
                String message = MessageUtils.getExceptionMessageString(ARGUMENTS_NOT_LEGAL_CC_ATTRS_EXPR);
                throw new TagAttributeException(this, message);
            }
          
            ExpressionFactory f = ctx.getExpressionFactory();
            ValueExpression valueExpression = f.createValueExpression(ctx, expr, type);
            if ((eq && hasCompositeComponentExpr()) || (!eq && ELUtils.isCompositeComponentExpr(expr))) {
                valueExpression = new ContextualCompositeValueExpression(getLocation(), valueExpression);
            }
            return new TagValueExpression(this, valueExpression);
            
        } catch (TagAttributeException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TagAttributeException(this, e);
        }
    }
    
    private Boolean hasCompositeComponentExpr() {
        if (hasCompositeComponentExpr == null) {
            hasCompositeComponentExpr = ELUtils.isCompositeComponentExpr(this.value);
        }
        return hasCompositeComponentExpr;
    }
    
    private Boolean hasCompositeComponentLookupWithArgs() {
        if (hasCompositeComponentLookupWithArgs == null) {
            hasCompositeComponentLookupWithArgs = hasCompositeComponentExpr() && 
                    ELUtils.isCompositeComponentLookupWithArgs(this.value);
        }
        return hasCompositeComponentLookupWithArgs;
    }
    
    private Boolean hasCompositeComponentMethodExprLookup() {
        if (hasCompositeComponentMethodExprLookup == null) {
            hasCompositeComponentMethodExprLookup =
                    hasCompositeComponentExpr() &&
                    !hasCompositeComponentLookupWithArgs() &&
                    ELUtils.isCompositeComponentMethodExprLookup(this.value);
        }
        return hasCompositeComponentMethodExprLookup;
    }


    // ---------------------------------------------------------- Nested Classes


    private static class AttributeLookupMethodExpression extends MethodExpression {

        private static final long serialVersionUID = -8983924930720420664L;
        private ValueExpression lookupExpression;


        public AttributeLookupMethodExpression(ValueExpression lookupExpression) {

            Util.notNull("lookupExpression", lookupExpression);
            this.lookupExpression = lookupExpression;

        }

        @SuppressWarnings({"UnusedDeclaration"})
        public AttributeLookupMethodExpression() {} // for serialization

        @Override
        public MethodInfo getMethodInfo(ELContext elContext) {

            Util.notNull("elContext", elContext);
            Object result = lookupExpression.getValue(elContext);
            if (result != null && result instanceof MethodExpression) {
                return ((MethodExpression) result).getMethodInfo(elContext);
            }

            return null;

        }

        @Override
        public Object invoke(ELContext elContext, Object[] args) {

            Util.notNull("elContext", elContext);

            Object result = lookupExpression.getValue(elContext);
            if (result == null) {
                throw new FacesException("Unable to resolve composite component from using page using EL expression '" + lookupExpression.getExpressionString() + '\'');
            }
            if (!(result instanceof MethodExpression)) {
                throw new FacesException("Successfully resolved expression '" + lookupExpression.getExpressionString() + "', but the value is not a MethodExpression");
            }

            return ((MethodExpression) result).invoke(elContext, args);

        }

        @Override
        public String getExpressionString() {

            return lookupExpression.getExpressionString();

        }

        @Override
        public boolean equals(Object otherObj) {

            boolean result = false;
            if (otherObj instanceof AttributeLookupMethodExpression) {
                AttributeLookupMethodExpression other =
                        (AttributeLookupMethodExpression) otherObj;
                result = lookupExpression.getExpressionString().equals(other.lookupExpression.getExpressionString());
            }
            return result;

        }

        @Override
        public boolean isLiteralText() {

            return lookupExpression.isLiteralText();

        }

        @Override
        public int hashCode() {

            return lookupExpression.hashCode();

        }

    } // END AttributeLookupMethodExpression
}
