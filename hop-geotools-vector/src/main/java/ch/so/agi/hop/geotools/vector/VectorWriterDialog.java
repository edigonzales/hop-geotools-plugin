package ch.so.agi.hop.geotools.vector;

import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class VectorWriterDialog extends BaseTransformDialog {

  private final VectorWriterMeta input;
  private TextVar wFileName;
  private TextVar wLayerName;
  private ComboVar wGeometryField;

  public VectorWriterDialog(
      Shell parent, IVariables variables, VectorWriterMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    setShellImage(shell, input);
    shell.setText("Vector Writer");
    shell.setMinimumSize(720, 330);

    changed = input.hasChanged();
    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);
    int margin = PropsUi.getMargin();

    wlTransformName = new Label(shell, SWT.RIGHT);
    wlTransformName.setText("Transform name");
    PropsUi.setLook(wlTransformName);
    fdlTransformName = new FormData();
    fdlTransformName.left = new FormAttachment(0, 0);
    fdlTransformName.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlTransformName.top = new FormAttachment(0, margin);
    wlTransformName.setLayoutData(fdlTransformName);

    wTransformName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wTransformName.setText(transformName);
    PropsUi.setLook(wTransformName);
    fdTransformName = new FormData();
    fdTransformName.left = new FormAttachment(props.getMiddlePct(), 0);
    fdTransformName.right = new FormAttachment(100, 0);
    fdTransformName.top = new FormAttachment(0, margin);
    wTransformName.setLayoutData(fdTransformName);

    Label wlFile = new Label(shell, SWT.RIGHT);
    wlFile.setText("Output file");
    PropsUi.setLook(wlFile);
    FormData fdlFile = new FormData();
    fdlFile.left = new FormAttachment(0, 0);
    fdlFile.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlFile.top = new FormAttachment(wTransformName, margin * 2);
    wlFile.setLayoutData(fdlFile);

    Button wbFile = new Button(shell, SWT.PUSH | SWT.CENTER);
    wbFile.setText("Browse...");
    PropsUi.setLook(wbFile);
    FormData fdbFile = new FormData();
    fdbFile.right = new FormAttachment(100, 0);
    fdbFile.top = new FormAttachment(wTransformName, margin * 2);
    wbFile.setLayoutData(fdbFile);

    wFileName = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wFileName);
    FormData fdFile = new FormData();
    fdFile.left = new FormAttachment(props.getMiddlePct(), 0);
    fdFile.right = new FormAttachment(wbFile, -margin);
    fdFile.top = new FormAttachment(wTransformName, margin * 2);
    wFileName.setLayoutData(fdFile);

    Label wlLayer = new Label(shell, SWT.RIGHT);
    wlLayer.setText("Layer (optional)");
    PropsUi.setLook(wlLayer);
    FormData fdlLayer = new FormData();
    fdlLayer.left = new FormAttachment(0, 0);
    fdlLayer.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlLayer.top = new FormAttachment(wFileName, margin);
    wlLayer.setLayoutData(fdlLayer);

    wLayerName = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wLayerName);
    FormData fdLayer = new FormData();
    fdLayer.left = new FormAttachment(props.getMiddlePct(), 0);
    fdLayer.right = new FormAttachment(100, 0);
    fdLayer.top = new FormAttachment(wFileName, margin);
    wLayerName.setLayoutData(fdLayer);

    Label wlGeometry = new Label(shell, SWT.RIGHT);
    wlGeometry.setText("Geometry input field");
    PropsUi.setLook(wlGeometry);
    FormData fdlGeometry = new FormData();
    fdlGeometry.left = new FormAttachment(0, 0);
    fdlGeometry.right = new FormAttachment(props.getMiddlePct(), -margin);
    fdlGeometry.top = new FormAttachment(wLayerName, margin);
    wlGeometry.setLayoutData(fdlGeometry);

    wGeometryField = new ComboVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wGeometryField);
    FormData fdGeometry = new FormData();
    fdGeometry.left = new FormAttachment(props.getMiddlePct(), 0);
    fdGeometry.right = new FormAttachment(100, 0);
    fdGeometry.top = new FormAttachment(wLayerName, margin);
    wGeometryField.setLayoutData(fdGeometry);

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText("OK");
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText("Cancel");
    setButtonPositions(new Button[] {wOk, wCancel}, margin, wGeometryField);

    BaseTransformDialog.getFieldsFromPrevious(variables, wGeometryField, pipelineMeta, transformMeta);

    wTransformName.addModifyListener(e -> input.setChanged());
    wFileName.addModifyListener(e -> input.setChanged());
    wLayerName.addModifyListener(e -> input.setChanged());
    wGeometryField.addModifyListener(e -> input.setChanged());
    wbFile.addListener(SWT.Selection, e -> browse());
    wOk.addListener(SWT.Selection, e -> ok());
    wCancel.addListener(SWT.Selection, e -> cancel());

    getData();
    input.setChanged(changed);
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private void browse() {
    FileDialog dialog = new FileDialog(shell, SWT.SAVE);
    dialog.setFilterExtensions(new String[] {"*.gpkg", "*.shp", "*.*"});
    dialog.setFilterNames(new String[] {"GeoPackage", "Shapefile", "All files"});
    String selected = dialog.open();
    if (selected != null) {
      wFileName.setText(selected);
    }
  }

  private void getData() {
    wFileName.setText(Utils.isEmpty(input.getFileName()) ? "" : input.getFileName());
    wLayerName.setText(Utils.isEmpty(input.getLayerName()) ? "" : input.getLayerName());
    wGeometryField.setText(Utils.isEmpty(input.getGeometryField()) ? "geometry" : input.getGeometryField());
    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    transformName = wTransformName.getText();
    input.setFileName(wFileName.getText());
    input.setLayerName(wLayerName.getText());
    input.setGeometryField(wGeometryField.getText());
    dispose();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }
}
