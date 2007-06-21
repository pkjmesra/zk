/* Grid.java

{{IS_NOTE
	Purpose:
		
	Description:
		
	History:
		Tue Oct 25 15:40:35     2005, Created by tomyeh
}}IS_NOTE

Copyright (C) 2005 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under GPL Version 2.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.zul;

import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Set;

import org.zkoss.lang.D;
import org.zkoss.lang.Objects;
import org.zkoss.lang.Classes;
import org.zkoss.lang.Exceptions;
import org.zkoss.util.logging.Log;
import org.zkoss.xml.HTMLs;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.ui.ext.render.ChildChangedAware;
import org.zkoss.zk.ui.ext.client.RenderOnDemand;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;

import org.zkoss.zul.impl.XulElement;
import org.zkoss.zul.ext.Paginal;
import org.zkoss.zul.event.ListDataEvent;
import org.zkoss.zul.event.ListDataListener;
import org.zkoss.zul.event.ZulEvents;
import org.zkoss.zul.event.PagingEvent;

/**
 * A grid is an element that contains both rows and columns elements.
 * It is used to create a grid of elements.
 * Both the rows and columns are displayed at once although only one will
 * typically contain content, while the other may provide size information.
 *
 * <p>Besides creating {@link Row} programmingly, you can assign
 * a data model (a {@link ListModel} instance) to a grid via
 * {@link #setModel} and then the grid will retrieve data
 * by calling {@link ListModel#getElementAt} when necessary.
 *
 * <p>Besides assign a list model, you could assign a renderer
 * (a {@link RowRenderer} instance) to a grid, such that
 * the grid will use this
 * renderer to render the data returned by {@link ListModel#getElementAt}.
 * If not assigned, the default renderer, which assumes a label per row,
 * is used.
 * In other words, the default renderer adds a label to
 * a row by calling toString against the object returned
 * by {@link ListModel#getElementAt}
 * 
 * <p>There are two ways to handle long content: scrolling and paging.
 * If {@link #getMold} is "default", scrolling is used if {@link #setHeight}
 * is called and too much content to display.
 * If {@link #getMold} is "paging", paging is used if two or more pages are
 * required. To control the number of rows to display in a page, use
 * {@link #setPageSize}.
 *
 * <p>If paging is used, the page controller is either created automatically
 * or assigned explicity by {@link #setPaginal}.
 * The paging controller specified explicitly by {@link #setPaginal} is called
 * the external page controller. It is useful if you want to put the paging
 * controller at different location (other than as a child component), or
 * you want to use the same controller to control multiple grids.
 *
 * <p>Default {@link #getSclass}: grid.
 * To have a grid withoug stripping, you might specify grid-no-striped as the sclass.
 * To have a grid without stripping and grid lines, you can specify any name,
 * other than grid and grid-on-striped, as the sclass.
 *
 * @author tomyeh
 * @see ListModel
 * @see RowRenderer
 * @see RowRendererExt
 */
public class Grid extends XulElement {
	private static final Log log = Log.lookup(Grid.class);

	private transient Rows _rows;
	private transient Columns _cols;
	private transient Foot _foot;
	private String _align;
	private ListModel _model;
	private RowRenderer _renderer;
	private transient ListDataListener _dataListener;
	/** The paging controller, used only if mold = "paging". */
	private transient Paginal _pgi;
	/** The paging controller, used only if mold = "paging" and user
	 * doesn't assign a controller via {@link #setPaginal}.
	 * If exists, it is the last child.
	 */
	private transient Paging _paging;
	private transient EventListener _pgListener;
	/** the # of rows to preload. */
	private int _preloadsz = 7;

	public Grid() {
		setSclass("grid");
	}

	/** Returns the rows.
	 */
	public Rows getRows() {
		return _rows;
	}
	/** Returns the columns.
	 */
	public Columns getColumns() {
		return _cols;
	}
	/** Returns the foot.
	 */
	public Foot getFoot() {
		return _foot;
	}

	/** Returns the specified cell, or null if not available.
	 * @param row which row to fetch (starting at 0).
	 * @param col which column to fetch (starting at 0).
	 */
	public Component getCell(int row, int col) {
		final Rows rows = getRows();
		if (rows == null) return null;

		List children = rows.getChildren();
		if (children.size() <= row) return null;

		children = ((Row)children.get(row)).getChildren();
		return children.size() <= col ? null: (Component)children.get(col);
	}

	/** Returns the horizontal alignment of the whole grid.
	 * <p>Default: null (system default: left unless CSS specified).
	 */
	public String getAlign() {
		return _align;
	}
	/** Sets the horizontal alignment of the whole grid.
	 * <p>Allowed: "left", "center", "right"
	 */
	public void setAlign(String align) {
		if (!Objects.equals(_align, align)) {
			_align = align;
			smartUpdate("align", _align);
		}
	}

	/** Re-initialize the grid at the client (actually, re-calculate
	 * the column width at the client).
	 */
	/*package*/ void initAtClient() {
		smartUpdate("z.init", true);
	}

	//--Paging--//
	/** Returns the paging controller, or null if not available.
	 * Note: the paging controller is used only if {@link #getMold} is "paging".
	 *
	 * <p>If mold is "paging", this method never returns null, because
	 * a child paging controller is created automcatically (if not specified
	 * by developers with {@link #setPaginal}).
	 *
	 * <p>If a paging controller is specified (either by {@link #setPaginal},
	 * or by {@link #setMold} with "paging"),
	 * the grid will rely on the paging controller to handle long-content
	 * instead of scrolling.
	 */
	public Paginal getPaginal() {
		return _pgi;
	}
	/* Specifies the paging controller.
	 * Note: the paging controller is used only if {@link #getMold} is "paging".
	 *
	 * <p>It is OK, though without any effect, to specify a paging controller
	 * even if mold is not "paging".
	 *
	 * @param pgi the paging controller. If null and {@link #getMold} is "paging",
	 * a paging controller is created automatically as a child component
	 * (see {@link #getPaging}).
	 */
	public void setPaginal(Paginal pgi) {
		if (!Objects.equals(pgi, _pgi)) {
			final Paginal old = _pgi;
			_pgi = pgi;

			if (inPagingMold()) {
				if (old != null) removePagingListener(old);
				if (_pgi == null) {
					if (_paging != null) _pgi = _paging;
					else newInternalPaging();
				} else { //_pgi != null
					if (_pgi != _paging) {
						if (_paging != null) _paging.detach();
						_pgi.setTotalSize(_rows != null ? _rows.getChildren().size(): 0);
						addPagingListener(_pgi);
					}
				}
			}
		}
	}
	/** Creates the internal paging component.
	 */
	private void newInternalPaging() {
		assert D.OFF || inPagingMold(): "paging mold only";
		assert D.OFF || (_paging == null && _pgi == null);

		final Paging paging = new Paging();
		paging.setAutohide(true);
		paging.setDetailed(true);
		paging.setTotalSize(_rows != null ? _rows.getChildren().size(): 0);
		paging.setParent(this);
		addPagingListener(_pgi);
	}
	/** Adds the event listener for the onPaging event. */
	private void addPagingListener(Paginal pgi) {
		if (_pgListener == null)
			_pgListener = new EventListener() {
				public void onEvent(Event event) {
					final PagingEvent evt = (PagingEvent)event;
					Events.postEvent(
						new PagingEvent(evt.getName(),
							Grid.this, evt.getPaginal(), evt.getActivePage()));
				}
			};
		pgi.addEventListener(ZulEvents.ON_PAGING, _pgListener);
	}
	/** Removes the event listener for the onPaging event. */
	private void removePagingListener(Paginal pgi) {
		pgi.removeEventListener(ZulEvents.ON_PAGING, _pgListener);
	}

	/** Called when the onPaging event is received (from {@link #getPaginal}).
	 *
	 * <p>Default: getRows().invalidate().
	 */
	public void onPaging() {
		if (_rows != null && _model != null && inPagingMold()) {
		//theorectically, _rows shall not be null if _model is not null when
		//this method is called. But, just in case -- if sent manually
			final Renderer renderer = new Renderer();
			try {
				final Paginal pgi = getPaginal();
				int pgsz = pgi.getPageSize();
				final int ofs = pgi.getActivePage() * pgsz;
				for (final Iterator it = _rows.getChildren().listIterator(ofs);
				--pgsz >= 0 && it.hasNext();)
					renderer.render((Row)it.next());
			} catch (Throwable ex) {
				renderer.doCatch(ex);
			} finally {
				renderer.doFinally();
			}
		}

		if (_rows != null) _rows.invalidate();
	}

	/** Returns the child paging controller that is created automatically,
	 * or null if mold is not "paging", or the controller is specified externally
	 * by {@link #setPaginal}.
	 */
	public Paging getPaging() {
		return _paging;
	}
	/** Returns the page size, aka., the number rows per page.
	 * @exception IllegalStateException if {@link #getPaginal} returns null,
	 * i.e., mold is not "paging" and no external controller is specified.
	 */
	public int getPageSize() {
		if (_pgi == null)
			throw new IllegalStateException("Available only the paging mold");
		return _pgi.getPageSize();
	}
	/** Sets the page size, aka., the number rows per page.
	 * @exception IllegalStateException if {@link #getPaginal} returns null,
	 * i.e., mold is not "paging" and no external controller is specified.
	 */
	public void setPageSize(int pgsz) {
		if (_pgi == null)
			throw new IllegalStateException("Available only the paging mold");
		_pgi.setPageSize(pgsz);
	}

	/** Returns whether this grid is in the paging mold.
	 */
	/*package*/ boolean inPagingMold() {
		return "paging".equals(getMold());
	}

	//-- ListModel dependent codes --//
	/** Returns the list model associated with this grid, or null
	 * if this grid is not associated with any list data model.
	 */
	public ListModel getModel() {
		return _model;
	}
	/** Sets the list model associated with this grid.
	 * If a non-null model is assigned, no matter whether it is the same as
	 * the previous, it will always cause re-render.
	 *
	 * @param model the list model to associate, or null to dis-associate
	 * any previous model.
	 * @exception UiException if failed to initialize with the model
	 */
	public void setModel(ListModel model) {
		if (model != null) {
			if (_model != model) {
				if (_model != null) {
					_model.removeListDataListener(_dataListener);
				} else {
					smartUpdate("z.model", "true");
				}

				initDataListener();
				_model = model;
				_model.addListDataListener(_dataListener);
			}

			//Always syncModel because it is easier for user to enfore reload
			syncModel(-1, -1); //create rows if necessary
			Events.postEvent("onInitRender", this, null);
			//Since user might setModel and setRender separately or repeatedly,
			//we don't handle it right now until the event processing phase
			//such that we won't render the same set of data twice
			//--
			//For better performance, we shall load the first few row now
			//(to save a roundtrip)
		} else if (_model != null) {
			_model.removeListDataListener(_dataListener);
			_model = null;
			if (_rows != null) _rows.getChildren().clear();
			smartUpdate("z.model", null);
		}
	}
	private void initDataListener() {
		if (_dataListener == null)
			_dataListener = new ListDataListener() {
				public void onChange(ListDataEvent event) {
					onListDataChange(event);
				}
			};
	}

	/** Returns the renderer to render each row, or null if the default
	 * renderer is used.
	 */
	public RowRenderer getRowRenderer() {
		return _renderer;
	}
	/** Sets the renderer which is used to render each row
	 * if {@link #getModel} is not null.
	 *
	 * <p>Note: changing a render will not cause the grid to re-render.
	 * If you want it to re-render, you could assign the same model again 
	 * (i.e., setModel(getModel())), or fire an {@link ListDataEvent} event.
	 *
	 * @param renderer the renderer, or null to use the default.
	 * @exception UiException if failed to initialize with the model
	 */
	public void setRowRenderer(RowRenderer renderer) {
		_renderer = renderer;
	}
	/** Sets the renderer by use of a class name.
	 * It creates an instance automatically.
	 */
	public void setRowRenderer(String clsnm)
	throws ClassNotFoundException, NoSuchMethodException,
	InstantiationException, java.lang.reflect.InvocationTargetException {
		if (clsnm != null)
			setRowRenderer((RowRenderer)Classes.newInstanceByThread(clsnm));
	}

	/** Returns the number of rows to preload when receiving
	 * the rendering request from the client.
	 *
	 * <p>Default: 7.
	 *
	 * <p>It is used only if live data ({@link #setModel} and
	 * not paging ({@link #getPaging}.
	 *
	 * @since 2.4.1
	 */
	public int getPreloadSize() {
		return _preloadsz;
	}
	/** Sets the number of rows to preload when receiving
	 * the rendering request from the client.
	 * <p>It is used only if live data ({@link #setModel} and
	 * not paging ({@link #getPaging}.
	 *
	 * @param sz the number of rows to preload. If zero, no preload
	 * at all.
	 * @exception UiException if sz is negative
	 * @since 2.4.1
	 */
	public void setPreloadSize(int sz) {
		if (sz < 0)
			throw new UiException("nonnegative is required: "+sz);
		_preloadsz = sz;
	}

	/** Synchronizes the grid to be consistent with the specified model.
	 *
	 * @param min the lower index that a range of invalidated rows
	 * @param max the higher index that a range of invalidated rows
	 */
	private void syncModel(int min, int max) {
		RowRenderer renderer = null;
		final int newsz = _model.getSize();
		final int oldsz = _rows != null ? _rows.getChildren().size(): 0;
		if (oldsz > 0) {
			if (newsz > 0 && min < oldsz) {
				if (max < 0 || max >= oldsz) max = oldsz - 1;
				if (max >= newsz) max = newsz - 1;
				if (min < 0) min = 0;

				for (Iterator it = _rows.getChildren().listIterator(min);
				min <= max && it.hasNext(); ++min) {
					final Row row = (Row)it.next();
					if (row.isLoaded()) {
						if (renderer == null)
							renderer = getRealRenderer();
						unloadRow(renderer, row);
					}
				}
			}

			//detach and remove
			if (oldsz > newsz) {
				for (Iterator it = _rows.getChildren().listIterator(newsz);
				it.hasNext();) {
					it.next();
					it.remove();
				}
			}
		}

		//auto create but it means <grid model="xx"><rows/>... will fail
		if (_rows == null)
			new Rows().setParent(this);

		for (int j = oldsz; j < newsz; ++j) {
			if (renderer == null)
				renderer = getRealRenderer();
			newUnloadedRow(renderer).setParent(_rows);
		}
	}
	/** Creates an new and unloaded row. */
	private final Row newUnloadedRow(RowRenderer renderer) {
		Row row = null;
		if (renderer instanceof RowRendererExt)
			row = ((RowRendererExt)renderer).newRow(this);

		if (row == null) {
			row = new Row();
			row.applyProperties();
		}
		row.setLoaded(false);

		newUnloadedCell(renderer, row);
		return row;
	}
	private Component newUnloadedCell(RowRenderer renderer, Row row) {
		Component cell = null;
		if (renderer instanceof RowRendererExt)
			cell = ((RowRendererExt)renderer).newCell(row);

		if (cell == null) {
			cell = newRenderLabel(null);
			cell.applyProperties();
		}
		cell.setParent(row);
		return cell;
	}
	/** Returns the label for the cell generated by the default renderer.
	 */
	private static Label newRenderLabel(String value) {
		final Label label =
			new Label(value != null && value.length() > 0 ? value: " ");
		label.setPre(true); //to make sure &nbsp; is generated, and then occupies some space
		return label;
	}
	/** Clears a row as if it is not loaded. */
	private final void unloadRow(RowRenderer renderer, Row row) {
		if (!(renderer instanceof RowRendererExt)
		|| (((RowRendererExt)renderer).getControls() & 
				RowRendererExt.DETACH_ON_UNLOAD) == 0) { //re-use (default)
			final List cells = row.getChildren();
			boolean bNewCell = cells.isEmpty();
			if (!bNewCell) {
				//detach and remove all but the first cell
				for (Iterator it = cells.listIterator(1); it.hasNext();) {
					it.next();
					it.remove();
				}

				final Component cell = (Component)cells.get(0);
				bNewCell = !(cell instanceof Label);
				if (bNewCell) {
					cell.detach();
				} else {
					((Label)cell).setValue("");
				}
			}

			if (bNewCell)
				newUnloadedCell(renderer, row);
			row.setLoaded(false);
		} else { //detach
			_rows.insertBefore(newUnloadedRow(renderer), row);
			row.detach();
		}
	}
	/** Handles a private event, onInitRender. It is used only for
	 * implementation, and you rarely need to invoke it explicitly.
	 */
	public void onInitRender() {
		final Renderer renderer = new Renderer();
		try {
			final int pgsz = inPagingMold() ? _pgi.getPageSize(): 20;
				//we don't know # of visible rows, so a 'smart' guess
				//It is OK since client will send back request if not enough
			int j = 0;
			for (Iterator it = _rows.getChildren().iterator();
			j < pgsz && it.hasNext(); ++j)
				renderer.render((Row)it.next());
		} catch (Throwable ex) {
			renderer.doCatch(ex);
		} finally {
			renderer.doFinally();
		}
	}

	/** Handles when the list model's content changed.
	 */
	private void onListDataChange(ListDataEvent event) {
		//when this is called _model is never null
		final int newsz = _model.getSize(), oldsz = _rows.getChildren().size();
		int min = event.getIndex0(), max = event.getIndex1();
		if (min < 0) min = 0;

		boolean done = false;
		switch (event.getType()) {
		case ListDataEvent.INTERVAL_ADDED:
			if (max < 0) max = newsz - 1;
			if ((max - min + 1) != (newsz - oldsz)) {
				log.warning("Conflict event: number of added rows not matched: "+event);
				break; //handle it as CONTENTS_CHANGED
			}

			RowRenderer renderer = null;
			final Row before =
				min < oldsz ? (Row)_rows.getChildren().get(min): null;
			for (int j = min; j <= max; ++j) {
				if (renderer == null)
					renderer = getRealRenderer();
				_rows.insertBefore(newUnloadedRow(renderer), before);
			}

			done = true;
			break;

		case ListDataEvent.INTERVAL_REMOVED:
			if (max < 0) max = oldsz - 1;
			if ((max - min + 1) != (oldsz - newsz)) {
				log.warning("Conflict event: number of removed rows not matched: "+event);
				break; //handle it as CONTENTS_CHANGED
			}

			//detach and remove
			for (Iterator it = _rows.getChildren().listIterator(min);
			it.hasNext();) {
				it.next();
				it.remove();
			}

			done = true;
			break;
		}

		if (!done) //CONTENTS_CHANGED
			syncModel(min, max);

		initAtClient();
			//client have to send back for what have to reload
	}

	private static final RowRenderer getDefaultRowRenderer() {
		return _defRend;
	}
	private static final RowRenderer _defRend = new RowRenderer() {
		public void render(Row row, Object data) {
			final Label label = newRenderLabel(Objects.toString(data));
			label.applyProperties();
			label.setParent(row);
			row.setValue(data);
		}
	};
	/** Returns the renderer used to render rows.
	 */
	private RowRenderer getRealRenderer() {
		return _renderer != null ? _renderer: getDefaultRowRenderer();
	}

	/** Used to render row if _model is specified. */
	private class Renderer implements java.io.Serializable {
		private final RowRenderer _renderer;
		private boolean _rendered, _ctrled;

		private Renderer() {
			_renderer = getRealRenderer();
		}
		private void render(Row row) throws Throwable {
			if (row.isLoaded())
				return; //nothing to do

			if (!_rendered && (_renderer instanceof RendererCtrl)) {
				((RendererCtrl)_renderer).doTry();
				_ctrled = true;
			}

			final Component cell = (Component)row.getChildren().get(0);
			if (!(_renderer instanceof RowRendererExt)
			|| (((RowRendererExt)_renderer).getControls() & 
				RowRendererExt.DETACH_ON_RENDER) != 0) { //detach (default)
				cell.detach();
			}

			try {
				_renderer.render(row, _model.getElementAt(row.getIndex()));
			} catch (Throwable ex) {
				try {
					final Label label = newRenderLabel(Exceptions.getMessage(ex));
					label.applyProperties();
					label.setParent(row);
				} catch (Throwable t) {
					log.error(t);
				}
				row.setLoaded(true);
				throw ex;
			} finally {
				if (row.getChildren().isEmpty())
					cell.setParent(row);
			}

			row.setLoaded(true);
			_rendered = true;
		}
		private void doCatch(Throwable ex) {
			if (_ctrled) {
				try {
					((RendererCtrl)_renderer).doCatch(ex);
				} catch (Throwable t) {
					throw UiException.Aide.wrap(t);
				}
			} else {
				throw UiException.Aide.wrap(ex);
			}
		}
		private void doFinally() {
			if (_rendered)
				initAtClient();
					//reason: after rendering, the column width might change
					//Also: Mozilla remembers scrollTop when user's pressing
					//RELOAD, it makes init more desirable.
			if (_ctrled)
				((RendererCtrl)_renderer).doFinally();
		}
	}

	/** Renders the specified {@link Row} if not loaded yet,
	 * with {@link #getRowRenderer}.
	 *
	 * <p>It does nothing if {@link #getModel} returns null.
	 * In other words, it is meaningful only if live data model is used.
	 */
	public void renderRow(Row row) {
		if (_model == null) return;

		final Renderer renderer = new Renderer();
		try {
			renderer.render(row);
		} catch (Throwable ex) {
			renderer.doCatch(ex);
		} finally {
			renderer.doFinally();
		}
	}
	/** Renders all {@link Row} if not loaded yet,
	 * with {@link #getRowRenderer}.
	 */
	public void renderAll() {
		if (_model == null) return;

		final Renderer renderer = new Renderer();
		try {
			for (Iterator it = _rows.getChildren().iterator(); it.hasNext();)
				renderer.render((Row)it.next());
		} catch (Throwable ex) {
			renderer.doCatch(ex);
		} finally {
			renderer.doFinally();
		}
	}
	/** Renders a set of specified rows.
	 * It is the same as {@link #renderItems}.
	 */
	public void renderRows(Set rows) {
		renderItems(rows);
	}

	public void renderItems(Set rows) {
		if (_model == null) { //just in case that app dev might change it
			if (log.debugable()) log.debug("No model no render");
			return;
		}

		if (rows.isEmpty())
			return; //nothing to do

		final Renderer renderer = new Renderer();
		try {
			for (Iterator it = rows.iterator(); it.hasNext();)
				renderer.render((Row)it.next());
		} catch (Throwable ex) {
			renderer.doCatch(ex);
		} finally {
			renderer.doFinally();
		}
	}

	//-- super --//
	public void setMold(String mold) {
		final String old = getMold();
		if (!Objects.equals(old, mold)) {
			super.setMold(mold);

			if ("paging".equals(old)) { //change from paging
				if (_paging != null) {
					removePagingListener(_paging);
					_paging.detach();
				} else if (_pgi != null) {
					removePagingListener(_pgi);
				}
			} else if (inPagingMold()) { //change to paging
				if (_pgi != null) addPagingListener(_pgi);
				else newInternalPaging();
			}
		}
	}
	public String getOuterAttrs() {
		final String attrs = super.getOuterAttrs();
		if (_align == null && _model == null)
			return attrs;

		final StringBuffer sb = new StringBuffer(80).append(attrs);
		if (_align != null)
			HTMLs.appendAttribute(sb, "align", _align);
		if (_model != null)
			HTMLs.appendAttribute(sb, "z.model", true);
		return sb.toString();
	}

	//-- Component --//
	public boolean insertBefore(Component newChild, Component refChild) {
		if (newChild instanceof Rows) {
			if (_rows != null && _rows != newChild)
				throw new UiException("Only one rows child is allowed: "+this+"\nNote: rows is created automatically if live data");
			_rows = (Rows)newChild;
		} else if (newChild instanceof Columns) {
			if (_cols != null && _cols != newChild)
				throw new UiException("Only one columns child is allowed: "+this);
			_cols = (Columns)newChild;
		} else if (newChild instanceof Foot) {
			if (_foot != null && _foot != newChild)
				throw new UiException("Only one foot child is allowed: "+this);
			_foot = (Foot)newChild;
		} else if (newChild instanceof Paging) {
			if (_pgi != null)
				throw new UiException("External paging cannot coexist with child paging");
			if (_paging != null && _paging != newChild)
				throw new UiException("Only one paging is allowed: "+this);
			if (!inPagingMold())
				throw new UiException("The child paging is allowed only in the paging mold");
			_pgi = _paging = (Paging)newChild;
		} else {
			throw new UiException("Unsupported child for grid: "+newChild);
		}
 
		if (super.insertBefore(newChild, refChild)) {
			invalidate();
			return true;
		}
		return false;
	}
	public boolean removeChild(Component child) {
		if (!super.removeChild(child))
			return false;

		if (_rows == child) _rows = null;
		else if (_cols == child) _cols = null;
		else if (_foot == child) _foot = null;
		else if (_paging == child) {
			_paging = null;
			if (_pgi == child) _pgi = null;
		}
		invalidate();
		return true;
	}

	//Cloneable//
	public Object clone() {
		final Grid clone = (Grid)super.clone();

		int cnt = 0;
		if (clone._rows != null) ++cnt;
		if (clone._cols != null) ++cnt;
		if (clone._foot != null) ++cnt;
		if (clone._paging != null) ++cnt;
		if (cnt > 0) clone.afterUnmarshal(cnt);

		return clone;
	}
	/** @param cnt # of children that need special handling (used for optimization).
	 * -1 means process all of them
	 */
	private void afterUnmarshal(int cnt) {
		for (Iterator it = getChildren().iterator(); it.hasNext();) {
			final Object child = it.next();
			if (child instanceof Rows) {
				_rows = (Rows)child;
				if (--cnt == 0) break;
			} else if (child instanceof Columns) {
				_cols = (Columns)child;
				if (--cnt == 0) break;
			} else if (child instanceof Foot) {
				_foot = (Foot)child;
				if (--cnt == 0) break;
			} else if (child instanceof Paging) {
				_pgi = _paging = (Paging)child;
				if (--cnt == 0) break;
			}
		}
	}

	//Serializable//
	private synchronized void readObject(java.io.ObjectInputStream s)
	throws java.io.IOException, ClassNotFoundException {
		s.defaultReadObject();
		afterUnmarshal(-1);
		//TODO: how to marshal _pgi if _pgi != _paging
		//TODO: re-register event listener for onPaging

		if (_model != null) initDataListener();
	}

	//-- ComponentCtrl --//
	protected Object newExtraCtrl() {
		return new ExtraCtrl();
	}
	/** A utility class to implement {@link #getExtraCtrl}.
	 * It is used only by component developers.
	 */
	protected class ExtraCtrl extends XulElement.ExtraCtrl
	implements ChildChangedAware, RenderOnDemand {
		//RenderOnDemand//
		public void renderItems(Set items) {
			int cnt = items.size();
			if (cnt == 0)
				return; //nothing to do
			cnt = 20 - cnt;
			if (cnt > 0 && _preloadsz > 0) { //Feature 1740072: pre-load
				if (cnt > _preloadsz) cnt = _preloadsz;

				//1. locate the first item found in items
				final List toload = new LinkedList();
				Iterator it = getRows().getChildren().iterator();
				while (it.hasNext()) {
					final Row row = (Row)it.next();
					if (items.contains(row)) //found
						break;
					if (!row.isLoaded())
						toload.add(0, row); //reverse order
				}

				//2. add unload items before the found one
				if (!toload.isEmpty()) {
					int bfcnt = cnt/3;
					for (Iterator e = toload.iterator();
					bfcnt > 0 && e.hasNext(); --bfcnt, --cnt) {
						items.add(e.next());
					}
				}

				//3. add unloaded after the found one
				while (cnt > 0 && it.hasNext()) {
					final Row row = (Row)it.next();
					if (!row.isLoaded() && items.add(row))
						--cnt;
				}
			}

			Grid.this.renderItems(items);
		}

		//ChildChangedAware//
		public boolean isChildChangedAware() {
			return true;
		}
	} 
}
