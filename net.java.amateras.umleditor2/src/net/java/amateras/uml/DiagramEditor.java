package net.java.amateras.uml;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.Iterator;
import java.util.List;

import net.java.amateras.uml.action.AbstractUMLEditorAction;
import net.java.amateras.uml.action.CopyAsImageAction;
import net.java.amateras.uml.action.OpenOutlineViewAction;
import net.java.amateras.uml.action.OpenPropertyViewAction;
import net.java.amateras.uml.action.SaveAsImageAction;
import net.java.amateras.uml.dnd.UMLDropTargetListenerFactory;
import net.java.amateras.uml.model.AbstractUMLEntityModel;
import net.java.amateras.uml.model.AbstractUMLModel;
import net.java.amateras.uml.model.RootModel;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.draw2d.LightweightSystem;
import org.eclipse.draw2d.Viewport;
import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.parts.ScrollableThumbnail;
import org.eclipse.gef.DefaultEditDomain;
import org.eclipse.gef.EditPartFactory;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.KeyHandler;
import org.eclipse.gef.KeyStroke;
import org.eclipse.gef.LayerConstants;
import org.eclipse.gef.SnapToGeometry;
import org.eclipse.gef.SnapToGrid;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.gef.editparts.ScalableRootEditPart;
import org.eclipse.gef.editparts.ZoomManager;
import org.eclipse.gef.palette.ConnectionCreationToolEntry;
import org.eclipse.gef.palette.CreationToolEntry;
import org.eclipse.gef.palette.PaletteEntry;
import org.eclipse.gef.requests.SimpleFactory;
import org.eclipse.gef.ui.actions.DeleteAction;
import org.eclipse.gef.ui.actions.DeleteRetargetAction;
import org.eclipse.gef.ui.actions.GEFActionConstants;
import org.eclipse.gef.ui.actions.PrintAction;
import org.eclipse.gef.ui.actions.RedoRetargetAction;
import org.eclipse.gef.ui.actions.UndoRetargetAction;
import org.eclipse.gef.ui.actions.ZoomInAction;
import org.eclipse.gef.ui.actions.ZoomOutAction;
import org.eclipse.gef.ui.parts.GraphicalEditorWithPalette;
import org.eclipse.gef.ui.parts.GraphicalViewerKeyHandler;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.util.TransferDropTargetListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

/**
 * GEF
 *
 * @author Takahiro Shida.
 */
public abstract class DiagramEditor extends GraphicalEditorWithPalette
	implements IPropertyChangeListener, IResourceChangeListener {

	private boolean savePreviouslyNeeded = false;
	private AbstractUMLEditorAction openOutlineAction  = null;
	private AbstractUMLEditorAction openPropertyAction = null;
	private AbstractUMLEditorAction saveAsImageAction  = null;
	private AbstractUMLEditorAction copyAsImageAction  = null;
	private boolean needViewerRefreshFlag = true;

	private KeyHandler sharedKeyHandler;

	public DiagramEditor() {
		super();
		setEditDomain(new DefaultEditDomain(this));
		getActionRegistry().registerAction(new UndoRetargetAction());
		getActionRegistry().registerAction(new RedoRetargetAction());
		getActionRegistry().registerAction(new DeleteRetargetAction());

		UMLPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(this);
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
	}

	protected void initializeGraphicalViewer() {
		GraphicalViewer viewer = getGraphicalViewer();
		IFile file = ((IFileEditorInput)getEditorInput()).getFile();
		RootModel root = null;
		if(file.exists()){
			try {
				root = DiagramSerializer.deserialize(file.getContents());
				validateModel(root);
			} catch(Exception ex){
				UMLPlugin.logException(ex);
			}
		}
		if(root == null){
			root = createInitializeModel();
		}
		viewer.setContents(root);
		addDndSupport(viewer, getDiagramType());
		applyPreferences();
	}

	private void refreshGraphicalViewer(){
		IEditorInput input = getEditorInput();
		if (input instanceof IFileEditorInput) {
			try {
				IFile file = ((IFileEditorInput) input).getFile();
				GraphicalViewer viewer = getGraphicalViewer();

				// desirialize
				RootModel newRoot = null;
				try {
					newRoot = DiagramSerializer.deserialize(file.getContents());
				} catch(Exception ex){
					UMLPlugin.logException(ex);
					return;
				}

				// copy to editing model
				RootModel root = (RootModel) viewer.getContents().getModel();
				root.copyFrom(newRoot);

			} catch (Exception ex) {
				UMLPlugin.logException(ex);
			}
		}
	}

	public void resourceChanged(final IResourceChangeEvent event) {
		if (event.getType() == IResourceChangeEvent.POST_CHANGE) {
			final IEditorInput input = getEditorInput();
			if (input instanceof IFileEditorInput) {
				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						IFile file = ((IFileEditorInput) input).getFile();
						if (!file.exists()) {
							IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
							page.closeEditor(DiagramEditor.this, false);
						} else {
							if (!getPartName().equals(file.getName())) {
								setPartName(file.getName());
							}
							if(needViewerRefreshFlag){
								refreshGraphicalViewer();
							} else {
								needViewerRefreshFlag = true;
							}
						}
					}
				});
			}
		}
	}

	public void propertyChange(PropertyChangeEvent event){
		applyPreferences();
	}

	protected void applyPreferences(){
		IPreferenceStore store = UMLPlugin.getDefault().getPreferenceStore();

		getGraphicalViewer().setProperty(SnapToGrid.PROPERTY_GRID_ENABLED,
		                new Boolean(store.getBoolean(UMLPlugin.PREF_SHOW_GRID)));
		getGraphicalViewer().setProperty(SnapToGrid.PROPERTY_GRID_VISIBLE,
		                new Boolean(store.getBoolean(UMLPlugin.PREF_SHOW_GRID)));

		int gridSize = store.getInt(UMLPlugin.PREF_GRID_SIZE);
		getGraphicalViewer().setProperty(SnapToGrid.PROPERTY_GRID_SPACING,
		                new Dimension(gridSize, gridSize));

		getGraphicalViewer().setProperty(SnapToGeometry.PROPERTY_SNAP_ENABLED,
		                new Boolean(store.getBoolean(UMLPlugin.PREF_SNAP_GEOMETRY)));
	}

	/**
	 * 댥멟궻긫?긙깈깛궳띿맟궠귢궫긲?귽깑귩렔벍뢇맫궥귡.
	 * -v2.0 릂럔듫똚궻믁돿
	 */
	private void validateModel(AbstractUMLEntityModel parent) {
		List<AbstractUMLModel> children = parent.getChildren();
		for (Iterator<AbstractUMLModel> iter = children.iterator(); iter.hasNext();) {
			AbstractUMLModel element = iter.next();
			if (element.getParent() == null) {
				element.setParent(parent);
			}
			if (element instanceof AbstractUMLEntityModel) {
				validateModel((AbstractUMLEntityModel) element);
			}
		}
	}
	/**
	 * 깓?긤궸렪봲궢궫뤾뜃궻룊딖긾긢깑귩뺅땛궥귡.
	 * @return
	 */
	protected abstract RootModel createInitializeModel();

	/**
	 * 긄긢귻?궻?귽긵귩뺅땛궥귡. (ex class)
	 * @return
	 */
	protected abstract String getDiagramType();

	/**
	 * Diagram긾긢깑귩뺅땛궥귡.
	 * @return
	 */
	public static final RootModel getActiveDiagramModel() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window != null) {
			IWorkbenchPage page = window.getActivePage();
			if (page != null) {
				IEditorPart editor = window.getActivePage().getActiveEditor();
				return (RootModel) editor.getAdapter(RootModel.class);
			}
		}
		return null;
	}

	/**
	 * @param viewer
	 */
	private void addDndSupport(GraphicalViewer viewer, String type) {
		IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint(UMLPlugin.PLUGIN_ID, "dnd");
		IExtension[] extensions = point.getExtensions();
		for (int i = 0; i < extensions.length; i++) {
			IExtension extension = extensions[i];
			IConfigurationElement[] elements = extension.getConfigurationElements();
			for (int j = 0; j < elements.length; j++) {
				IConfigurationElement element = elements[j];
				try {
					Object object = element.createExecutableExtension("class");
					if (object instanceof UMLDropTargetListenerFactory) {
						UMLDropTargetListenerFactory factory = (UMLDropTargetListenerFactory) object;
						if (factory.accept(type)) {
							TransferDropTargetListener targetListener = factory.getDropTargetListener(viewer);
							viewer.addDropTargetListener(targetListener);
						}
					}
				} catch (CoreException e) {
					e.printStackTrace();
				} finally {
				}
			}
		}
	}

	protected abstract EditPartFactory createEditPartFactory();

	protected void configureGraphicalViewer() {
		super.configureGraphicalViewer();
		GraphicalViewer viewer = getGraphicalViewer();
		viewer.setEditPartFactory(createEditPartFactory());

		ScalableRootEditPart rootEditPart = new ScalableRootEditPart();
		viewer.setRootEditPart(rootEditPart);

	    // ZoomManager궻롦벦
	    ZoomManager manager = rootEditPart.getZoomManager();

	    // 긛??깒긹깑궻먠믦
	    double[] zoomLevels = new double[] {
	      0.25,0.5,0.75,1.0,1.5,2.0,2.5,3.0,4.0,5.0,10.0,20.0
	    };
	    manager.setZoomLevels(zoomLevels);

	    // 긛?? 깒긹깑 긓깛긣깏긮깄?긘깈깛궻먠믦
	    ArrayList<String> zoomContributions = new ArrayList<String>();
	    zoomContributions.add(ZoomManager.FIT_ALL);
	    zoomContributions.add(ZoomManager.FIT_HEIGHT);
	    zoomContributions.add(ZoomManager.FIT_WIDTH);
	    manager.setZoomLevelContributions(zoomContributions);
	    // 둮묈귺긏긘깈깛궻띿맟궴뱋?
	    getActionRegistry().registerAction(new ZoomInAction(manager));
	    // 뢫룷귺긏긘깈깛궻띿맟궴뱋?
	    getActionRegistry().registerAction(new ZoomOutAction(manager));

        getGraphicalViewer().setKeyHandler(
                new GraphicalViewerKeyHandler(getGraphicalViewer()));

	    // 긓깛긡긏긚긣긽긦깄?궻띿맟
	    String menuId = this.getClass().getName() + ".EditorContext";


		MenuManager menuMgr = new MenuManager(menuId, menuId);
		openPropertyAction = new OpenPropertyViewAction(viewer);
		openOutlineAction  = new OpenOutlineViewAction(viewer);
		saveAsImageAction  = new SaveAsImageAction(viewer);
		copyAsImageAction  = new CopyAsImageAction(viewer);
		createDiagramAction(viewer);

		getSite().registerContextMenu(menuId, menuMgr, viewer);

		PrintAction printAction = new PrintAction(this);
		printAction.setImageDescriptor(UMLPlugin.getImageDescriptor("icons/print.gif"));
		getActionRegistry().registerAction(printAction);

		final DeleteAction deleteAction = new DeleteAction((IWorkbenchPart) this);
		deleteAction.setSelectionProvider(getGraphicalViewer());
		getActionRegistry().registerAction(deleteAction);
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				deleteAction.update();
			}
		});

		// Actions
//		IAction showRulers = new ToggleRulerVisibilityAction(getGraphicalViewer());
//		getActionRegistry().registerAction(showRulers);
//
//		IAction snapAction = new ToggleSnapToGeometryAction(getGraphicalViewer());
//		getActionRegistry().registerAction(snapAction);
//
//		IAction showGrid = new ToggleGridAction(getGraphicalViewer());
//		getActionRegistry().registerAction(showGrid);

		menuMgr.add(new Separator("edit"));
		menuMgr.add(getActionRegistry().getAction(ActionFactory.DELETE.getId()));
		menuMgr.add(getActionRegistry().getAction(ActionFactory.UNDO.getId()));
		menuMgr.add(getActionRegistry().getAction(ActionFactory.REDO.getId()));
//		menuMgr.add(getActionRegistry().getAction(ActionFactory.COPY.getId()));
//		menuMgr.add(getActionRegistry().getAction(ActionFactory.PASTE.getId()));
		menuMgr.add(new Separator("zoom"));
		menuMgr.add(getActionRegistry().getAction(GEFActionConstants.ZOOM_IN));
		menuMgr.add(getActionRegistry().getAction(GEFActionConstants.ZOOM_OUT));
		fillDiagramPopupMenu(menuMgr);
		menuMgr.add(new Separator("print"));
		menuMgr.add(saveAsImageAction);
		menuMgr.add(copyAsImageAction);
		menuMgr.add(printAction);
		menuMgr.add(new Separator("views"));
		menuMgr.add(openPropertyAction);
		menuMgr.add(openOutlineAction);
		menuMgr.add(new Separator("generate"));
		menuMgr.add(new Separator("additions"));
		viewer.setContextMenu(menuMgr);
		viewer.setKeyHandler(new GraphicalViewerKeyHandler(viewer)
				.setParent(getCommonKeyHandler()));

	}
	/**
	 * ?귽긵궸벫돸궢궫귺긏긘깈깛귩띿귡.
	 * @param viewer
	 */
	protected abstract void createDiagramAction(GraphicalViewer viewer);

	/**
	 * 귺긏긘깈깛귩긓깛긡긌긚긣긽긦깄?궸먠믦궥귡.
	 * @param manager
	 */
	protected abstract void fillDiagramPopupMenu(MenuManager manager);

	protected void setInput(IEditorInput input) {
		super.setInput(input);
		setPartName(input.getName());
	}

	public void doSave(IProgressMonitor monitor) {
		try {
			IEditorInput input = getEditorInput();
			if(input instanceof IFileEditorInput){
				needViewerRefreshFlag = false;
				IFile file = ((IFileEditorInput)input).getFile();
				file.setContents(DiagramSerializer.serialize((RootModel)getGraphicalViewer().getContents().getModel()),
						true,true,monitor);
			}
		} catch(Exception ex){
			throw new RuntimeException(ex);
		}
		getCommandStack().markSaveLocation();
	}

	public void doSaveAs() {
		doSave(new NullProgressMonitor());
	}

	public boolean isSaveAsAllowed() {
		return true;
	}

	public void dispose(){
		UMLPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(this);
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
		super.dispose();
	}

	public void commandStackChanged(EventObject event) {
		if (isDirty()) {
			if (!savePreviouslyNeeded()) {
				setSavePreviouslyNeeded(true);
				firePropertyChange(IEditorPart.PROP_DIRTY);
			}
		} else {
			setSavePreviouslyNeeded(false);
			firePropertyChange(IEditorPart.PROP_DIRTY);
		}
		super.commandStackChanged(event);
	}

	private void setSavePreviouslyNeeded(boolean value) {
		this.savePreviouslyNeeded = value;
	}

	private boolean savePreviouslyNeeded() {
		return this.savePreviouslyNeeded;
	}

	/**
	 * 긄깛긡귻긡귻뾭궻PaletteEntry귩띿맟궢귏궥갃
	 *
	 * @param itemName 긬깒긞긣궸?렑궥귡귺귽긡?뼹
	 * @param clazz 긾긢깑궻긏깋긚
	 * @param icon 긬깒긞긣궸?렑궥귡귺귽긓깛궻긬긚
	 * @return PaletteEntry
	 */
	protected PaletteEntry createEntityEntry(String itemName,Class<?> clazz,String icon){
		CreationToolEntry entry = new CreationToolEntry(
				itemName,
				itemName,
				new SimpleFactory(clazz),
				UMLPlugin.getImageDescriptor(icon),
				UMLPlugin.getImageDescriptor(icon));
		return entry;
	}

	/**
	 * 긓긨긏긘깈깛뾭궻PaletteEntry귩띿맟궢귏궥갃
	 *
	 * @param itemName 긬깒긞긣궸?렑궥귡귺귽긡?뼹
	 * @param clazz 긾긢깑궻긏깋긚
	 * @param icon 긬깒긞긣궸?렑궥귡귺귽긓깛궻긬긚
	 * @return PaletteEntry
	 */
	protected PaletteEntry createConnectionEntry(String itemName,Class<?> clazz,String icon){
		ConnectionCreationToolEntry entry = new ConnectionCreationToolEntry(
				itemName,
				itemName,
				new SimpleFactory(clazz),
				UMLPlugin.getImageDescriptor(icon),
				UMLPlugin.getImageDescriptor(icon));
		return entry;
	}

	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		super.selectionChanged(part,selection);
		if (selection instanceof IStructuredSelection) {
			openPropertyAction.update((IStructuredSelection)selection);
			openOutlineAction.update((IStructuredSelection)selection);
			saveAsImageAction.update((IStructuredSelection)selection);
			updateDiagramAction(selection);
		}
	}

	/**
	 * Returns the KeyHandler with common bindings for both the Outline and Graphical Views.
	 * For example, delete is a common action.
	 */
	protected KeyHandler getCommonKeyHandler(){
		if (sharedKeyHandler == null){
			sharedKeyHandler = new KeyHandler();
			sharedKeyHandler.put(
				KeyStroke.getPressed(SWT.F2, 0),
				getActionRegistry().getAction(GEFActionConstants.DIRECT_EDIT));
		}
		return sharedKeyHandler;
	}

	/**
	 * 귺긏긘깈깛귩뛛륷궥귡.
	 * @param selection
	 */
	protected abstract void updateDiagramAction(ISelection selection);

	@SuppressWarnings("rawtypes")
	public Object getAdapter(Class type) {
		if (type == ZoomManager.class){
			return ((ScalableRootEditPart) getGraphicalViewer().getRootEditPart()).getZoomManager();
		}
		if (type == IContentOutlinePage.class) {
			return new UMLContentOutlinePage();
		}
		if (type == RootModel.class){
			return getGraphicalViewer().getContents().getModel();
		}
		if (type == CommandStack.class) {
			return getCommandStack();
		}
		return super.getAdapter(type);
	}

	/**
	 * UML긄긢귻?궻귺긂긣깋귽깛긻?긙갃
	 * ?귽귺긐깋?궻긖?긨귽깑귩?렑궢귏궥갃
	 */
	private class UMLContentOutlinePage implements IContentOutlinePage {

		private Canvas canvas;
		private ScrollableThumbnail thumbnail;
		private DisposeListener disposeListener;

		public void createControl(Composite parent) {
			this.canvas = new Canvas(parent, SWT.BORDER);
			LightweightSystem lws = new LightweightSystem(canvas);

			// RootEditPart궻긮깄?귩??긚궴궢궲긖?긨귽깑귩띿맟
			ScalableRootEditPart rootEditPart = (ScalableRootEditPart)getGraphicalViewer().getRootEditPart();
			this.thumbnail = new ScrollableThumbnail((Viewport)rootEditPart.getFigure());
			this.thumbnail.setSource(rootEditPart.getLayer(LayerConstants.PRINTABLE_LAYERS));
			lws.setContents(thumbnail);

			// 귽긽?긙귩봨딙궥귡궫귕궻깏긚긥
			disposeListener = new DisposeListener() {
				public void widgetDisposed(DisposeEvent e) {
					if (thumbnail != null) {
						thumbnail.deactivate();
						thumbnail = null;
					}
				}
			};

			// 긐깋긲귻긇깑갋긮깄?깗궕봨딙궠귢귡궴궖궸긖?긨귽깑귖봨딙궥귡
			getGraphicalViewer().getControl().addDisposeListener(disposeListener);
		}

		public Control getControl(){
			return this.canvas;
		}

		public void dispose() {
			if (getGraphicalViewer().getControl() != null && !getGraphicalViewer().getControl().isDisposed()){
				getGraphicalViewer().getControl().removeDisposeListener(disposeListener);
			}
		}

		public void setActionBars(IActionBars actionBars) {
			// nothing to do
		}

		public void setFocus() {
			// nothing to do
		}

		public void addSelectionChangedListener(ISelectionChangedListener listener) {
			// nothing to do
		}

		public ISelection getSelection() {
			// nothing to do
			return null;
		}

		public void removeSelectionChangedListener(ISelectionChangedListener listener) {
			// nothing to do
		}

		public void setSelection(ISelection selection) {
			// nothing to do
		}
	}


}
