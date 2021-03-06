/* uDig - User Friendly Desktop Internet GIS client
 * http://udig.refractions.net
 * (C) 2004-2012, Refractions Research Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * (http://www.eclipse.org/legal/epl-v10.html), and the Refractions BSD
 * License v1.0 (http://udig.refractions.net/files/bsd3-v10.html).
 */
package net.refractions.udig.render.internal.gridcoverage.basic;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Collections;

import javax.media.jai.InterpolationNearest;
import javax.media.jai.JAI;
import javax.media.jai.TileCache;

import net.refractions.udig.catalog.IGeoResource;
import net.refractions.udig.catalog.rasterings.GridCoverageLoader;
import net.refractions.udig.project.ILayer;
import net.refractions.udig.project.internal.ProjectPlugin;
import net.refractions.udig.project.internal.StyleBlackboard;
import net.refractions.udig.project.internal.render.impl.RendererImpl;
import net.refractions.udig.project.render.IRenderContext;
import net.refractions.udig.project.render.RenderException;
import net.refractions.udig.project.render.displayAdapter.IMapDisplay;
import net.refractions.udig.render.gridcoverage.basic.internal.Messages;
import net.refractions.udig.ui.graphics.SLDs;

import org.eclipse.core.runtime.IProgressMonitor;
import org.geotools.coverage.CoverageFactoryFinder;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.image.ImageWorker;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.geotools.renderer.lite.RendererUtilities;
import org.geotools.renderer.lite.gridcoverage2d.GridCoverageRenderer;
import org.geotools.styling.FeatureTypeStyle;
import org.geotools.styling.RasterSymbolizer;
import org.geotools.styling.Rule;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.opengis.coverage.grid.GridCoverage;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.filter.expression.Expression;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Render raster information from memory.
 * <p>
 * This renderer is expensive in terms of memory but is the best choice for formats
 * such as jpeg which are not optimised for crop operation (ie you need
 * to read in the entire image to draw even a small part of it).
 * <p>
 * Initially the rendering system looks at the style for:
 * <ul>
 * <li>scale ranges (handled in the rendering metrics)</li>
 * <li>opacity</li>
 * </ul>
 * Now that raster symbolizer support is available in GeoTools we may be able to do better
 * and handle things like color maps?
 * @see GridCoverageLoader
 * @author Jesse Eichar
 * @author Andrea Aime
 * @version $Revision: 1.9 $
 */
public class MemoryGridCoverageRenderer extends RendererImpl {
    /**
     * Internal GridCoverageRenderer used to perform the drawing
     * (chances are we could use the normal streaming renderer here
     *  and perform a bit better?)
     */
    private GridCoverageRenderer renderer;
    
    /**
     * Renderer using a GridCoverageLoader to render out of memory
     * (used to load jpeg into memory; or can be used when user requests cache)
     */
    public MemoryGridCoverageRenderer(){        
    }
    @SuppressWarnings("unchecked")
	public synchronized void render( Graphics2D graphics, IProgressMonitor monitor )
            throws RenderException {
        try {
        	// get the current context
        	final IRenderContext currentContext = getContext();       	
        	
        	//check that actually we have something to draw
            currentContext.setStatus(ILayer.WAIT);
            currentContext.setStatusMessage(Messages.BasicGridCoverageRenderer_rendering_status);
            
            //get the envelope and the screen extent
            ReferencedEnvelope envelope = getRenderBounds();
            if( envelope == null || envelope.isNull()){
                envelope = context.getImageBounds();
            }
            Point upperLeft = currentContext.worldToPixel( new Coordinate( envelope.getMinX(), envelope.getMinY()) );
            Point bottomRight = currentContext.worldToPixel( new Coordinate( envelope.getMaxX(), envelope.getMaxY()) );
            Rectangle screenSize = new Rectangle( upperLeft );
            screenSize.add( bottomRight );
        	IMapDisplay mapDisplay = currentContext.getMapDisplay();
            
        	CoordinateReferenceSystem destinationCRS = currentContext.getCRS();

            final IGeoResource geoResource = currentContext.getGeoResource();
            ReferencedEnvelope bounds = (ReferencedEnvelope) currentContext.getImageBounds();
            bounds=bounds.transform(destinationCRS, true);
            
            GridEnvelope range=new GridEnvelope2D(0,0, mapDisplay.getWidth(), mapDisplay.getHeight() );
            
            MathTransform displayToLayer=currentContext.worldToScreenMathTransform().inverse();
            ReferencingFactoryFinder.getMathTransformFactory(null).createConcatenatedTransform(displayToLayer, currentContext.getLayer().mapToLayerTransform()); 
            GridGeometry2D geom=new GridGeometry2D(range, displayToLayer, destinationCRS );

            currentContext.setStatus(ILayer.WORKING);
            setState( STARTING );
            
            GridCoverageLoader loader = geoResource.resolve(GridCoverageLoader.class, monitor);
            if( loader == null ){
                // unable to load in memory
                return;
            }
            GridCoverage2D coverage = (GridCoverage2D) loader.load(geom, monitor);

            if(coverage!=null)
			{
	            
	            //setting rendering hints
	            RenderingHints hints = new RenderingHints(Collections.EMPTY_MAP);
	            hints.add(new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED));
	            hints.add(new RenderingHints(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE));
	            hints.add(new RenderingHints(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED));
	            hints.add(new RenderingHints(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED));
	            hints.add(new RenderingHints(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR));
	            hints.add(new RenderingHints(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE));
	            hints.add(new RenderingHints(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF));
	            hints.add(new RenderingHints(JAI.KEY_INTERPOLATION,new InterpolationNearest()));
	            graphics.addRenderingHints(hints);
	            
	            final TileCache tempCache=currentContext.getTileCache();
	            hints.add(new RenderingHints(JAI.KEY_TILE_CACHE,tempCache));
	            
	            if( CRS.getHorizontalCRS(destinationCRS) == null ){
	                destinationCRS = coverage.getCoordinateReferenceSystem2D();
	            }
	            //draw
	            try {
	                Style style = grabStyle();
	                Rule rule = SLDs.getRasterSymbolizerRule(style);
	                
	                final double currentScale = currentContext.getViewportModel().getScaleDenominator();                
	                double minScale = rule.getMinScaleDenominator();
	                double maxScale = rule.getMaxScaleDenominator();
	                if (minScale <= currentScale && currentScale <= maxScale ) {
	                    final GridCoverageRenderer paint = new GridCoverageRenderer( destinationCRS, envelope, screenSize,null, hints );
	                    final RasterSymbolizer rasterSymbolizer = SLD.rasterSymbolizer(style);
	                
                        // check if there is a color to mask
                        Object maskColor = getContext().getLayer().getStyleBlackboard().getString("raster-color-mask"); //$NON-NLS-1$                       
                        if (maskColor instanceof String) {
                            // create a color mask
                            String[] colorSplit = ((String) maskColor).split(":"); //$NON-NLS-1$
                            Color color = new Color(Integer.parseInt(colorSplit[0]), Integer.parseInt(colorSplit[1]),
                                    Integer.parseInt(colorSplit[2]));
                            RenderedImage image = coverage.getRenderedImage();
                            ImageWorker iw = new ImageWorker(image);
                            iw.makeColorTransparent(color);
                            image = iw.getRenderedImage();
                            GridCoverageFactory gcF = CoverageFactoryFinder.getGridCoverageFactory(null);
                            coverage = gcF.create(coverage.getName(), image, coverage.getCoordinateReferenceSystem(), coverage
                                    .getGridGeometry().getGridToCRS(), coverage.getSampleDimensions(), null, null);
                        }
	                    
	                    //setState( RENDERING );
	                    paint.paint( graphics, coverage, rasterSymbolizer );                        
	                    setState( DONE );
	                }
	                
	            } catch(Exception e) {
	                final GridCoverageRenderer paint = new GridCoverageRenderer( destinationCRS, envelope, screenSize,null, hints );
	                RasterSymbolizer rasterSymbolizer = CommonFactoryFinder.getStyleFactory(null).createRasterSymbolizer();
	                
	                //setState( RENDERING );
	                paint.paint( graphics, coverage, rasterSymbolizer );
                    setState( DONE );
	            }
	            //tempCache.flush();
			}
        } catch (Exception e1) {
            throw new RenderException(e1);
        }
        finally {
            getContext().setStatus(ILayer.DONE);
            getContext().setStatusMessage(null);
        }
    }

    /**
     *  grab the style from the blackboard, otherwise return null
     */
    private Style grabStyle() {
        // check for style information on the blackboard
        StyleBlackboard styleBlackboard = (StyleBlackboard) getContext()
                .getLayer().getStyleBlackboard();
        
        Style style = (Style) styleBlackboard.lookup(Style.class);
        
        return style;
    }
    /*
    public synchronized void render2( Graphics2D graphics, IProgressMonitor monitor )
            throws RenderException {
        State state = null;
        try {
            state = prepareRender(monitor);
        } catch (IOException e1) {
            throw new RenderException(e1);
        }

        doRender(renderer, graphics, state);
    }*/
    /**
     * Renders a GridCoverage
     * 
     * @param renderer
     * @param graphics
     */
    public void doRender( GridCoverageRenderer renderer, Graphics2D graphics, GridCoverageRenderState state ) {
        double scale = state.context.getViewportModel().getScaleDenominator();
        if (scale < state.minScale || scale > state.maxScale)
            return;

        state.context.setStatus(ILayer.WAIT);
        state.context.setStatusMessage(Messages.BasicGridCoverageRenderer_rendering_status);

        // setup composite
        Composite oldComposite = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, state.opacity));

        // setup affine transform for on screen rendering
        Rectangle displayArea = state.displayArea;

        AffineTransform at = RendererUtilities.worldToScreenTransform(state.bounds, displayArea);
        AffineTransform tempTransform = graphics.getTransform();
        AffineTransform atg = new AffineTransform(tempTransform);
        atg.concatenate(at);
        graphics.setTransform(atg);

        GridCoverage coverage;
        try {
            coverage = getContext().getGeoResource().resolve(GridCoverage.class, null);

            RasterSymbolizer rasterSymbolizer;

            StyleFactory factory = CommonFactoryFinder.getStyleFactory(null);
            rasterSymbolizer = factory.createRasterSymbolizer();

            renderer.paint(graphics, (GridCoverage2D) coverage, rasterSymbolizer);
        } catch (IOException e) {
            // TODO Handle IOException
            throw (RuntimeException) new RuntimeException().initCause(e);
        } catch (FactoryException e) {
            throw (RuntimeException) new RuntimeException().initCause(e);
        } catch (TransformException e) {
            throw (RuntimeException) new RuntimeException().initCause(e);
        } catch (NoninvertibleTransformException e) {
            throw (RuntimeException) new RuntimeException().initCause(e);
        }

        // reset previous configuration
        graphics.setComposite(oldComposite);
        graphics.setTransform(tempTransform);

        if (state.context.getStatus() == ILayer.WAIT) {
            // status hasn't changed... everything looks good
            state.context.setStatus(ILayer.DONE);
            state.context.setStatusMessage(null);
        }

    }

    /**
     * Extract symbolizer parameters from the style blackboard
     */
    public static GridCoverageRenderState getRenderState( IRenderContext context ) {
        StyleBlackboard styleBlackboard = (StyleBlackboard) context.getLayer().getStyleBlackboard();
        Style style = (Style) styleBlackboard.lookup(Style.class);
        double minScale = Double.MIN_VALUE;
        double maxScale = Double.MAX_VALUE;
        float opacity = 1.0f;
        if (style != null) {
            try {
                FeatureTypeStyle featureStyle = style.featureTypeStyles().get(0);                
                Rule rule = featureStyle.rules().get(0);
                minScale = rule.getMinScaleDenominator();
                maxScale = rule.getMaxScaleDenominator();
                if (rule.getSymbolizers()[0] instanceof RasterSymbolizer) {
                    RasterSymbolizer rs = (RasterSymbolizer) rule.getSymbolizers()[0];
                    opacity = getOpacity(rs);
                }

            } catch (Exception e) {
                ProjectPlugin.getPlugin().log(e);
            }
        } else {
            opacity = 1;
            minScale = 0;
            maxScale = Double.MAX_VALUE;
        }

        Rectangle displayArea = new Rectangle(context.getMapDisplay().getWidth(), context
                .getMapDisplay().getHeight());

        return new GridCoverageRenderState(context, context.getImageBounds(), displayArea, opacity,
                minScale, maxScale);
    }

    private static float getOpacity( RasterSymbolizer sym ) {
        float alpha = 1.0f;
        Expression exp = sym.getOpacity();
        if (exp == null)
            return alpha;
        Object obj = exp.evaluate(null);
        if (obj == null)
            return alpha;
        Number num = null;
        if (obj instanceof Number)
            num = (Number) obj;
        if (num == null)
            return alpha;
        return num.floatValue();
    }

    private GridCoverageRenderState prepareRender( IProgressMonitor monitor ) throws IOException {

        try {
            CoordinateReferenceSystem contextCRS = getContext().getCRS();
            Rectangle rectangle = new Rectangle(getContext().getMapDisplay().getDisplaySize());
            Envelope bounds = getRenderBounds();
            if (bounds == null) {
                // show the bounds of the context
                bounds = getContext().getImageBounds();
                if (bounds instanceof ReferencedEnvelope) {
                    ReferencedEnvelope all = (ReferencedEnvelope) bounds;
                    if (!contextCRS.equals(all.getCoordinateReferenceSystem())) {
                        bounds = all.transform(contextCRS, true, 10);
                    }
                } else {
                    // this should not happen!
                    ReferencedEnvelope all = new ReferencedEnvelope(bounds, getContext()
                            .getViewportModel().getCRS());
                    bounds = all.transform(contextCRS, true, 10);
                }
            }
            AffineTransform world2screen = null;
            renderer = new GridCoverageRenderer(contextCRS, bounds, rectangle,world2screen);

        } catch (TransformException e) {
            // TODO Handle TransformException
            throw (RuntimeException) new RuntimeException().initCause(e);
        } catch (NoninvertibleTransformException e) {
            // TODO Handle NoninvertibleTransformException
            throw (RuntimeException) new RuntimeException().initCause(e);
        } catch (FactoryException e) {
            // TODO Handle FactoryException
            throw (RuntimeException) new RuntimeException().initCause(e);
        }
        return getRenderState(getContext());
    }

    public void stopRendering() {
        setState(STATE_EDEFAULT);
    }

    public void dispose() {
        // TODO
    }

    public void render( IProgressMonitor monitor ) throws RenderException {
        render(getContext().getImage().createGraphics(), monitor);
    }

}
