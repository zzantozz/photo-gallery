package rds.photogallery;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class PhotoFrame {
    private final JFrame theFrame;
    private JButton newFrameButton;
    private JButton lessColumnsButton;
    private JTextField columnsField;
    private JButton moreColumnsButton;
    private JButton lessRowsButton;
    private JTextField rowsField;
    private JButton moreRowsButton;
    private JPanel mainDisplayArea;
    private JPanel mainPanel;
    private JPanel controlPanel;
    private PhotoPanel photoPanel;
    private final String name;
    private final PersistentFrameState frameState;
    private List<Function<PhotoFrame, Void>> disposeListeners = new ArrayList<>();

    public PhotoFrame(String name, PersistentFrameState frameState) {
        this.name = name;
        this.frameState = frameState;
//        setUpControls(currentFrameConfiguration().getRows(), currentFrameConfiguration().getColumns());
//        addHotKeys();
//        setShowingRatings(currentFrameConfiguration().isShowingRatings());
//        setShowingNames(currentFrameConfiguration().isShowingNames());
//        setShowingTags(currentFrameConfiguration().isShowingTags());
        this.photoPanel = new PhotoPanel();
        this.theFrame = buildInitialJFrame();
//        cloneButton.addActionListener(e -> Application.instance.newPhotoFrame(PhotoFrameAdvanced.this));
    }

    private JFrame buildInitialJFrame() {
        final FrameConfiguration frameConfiguration;
        if (this.frameState.isFullScreen()) {
            frameConfiguration = this.frameState.getFullScreenConfig();
        } else {
            frameConfiguration = this.frameState.getNormalConfig();
        }
        final JFrame result = new JFrame();
        result.setTitle("Photo Gallery " + name);
        result.setContentPane(mainPanel);
        mainDisplayArea.setLayout(new GridLayout(1, 1));
        mainDisplayArea.add(photoPanel);
        result.setBounds(frameConfiguration.getX(), frameConfiguration.getY(), frameConfiguration.getWidth(), frameConfiguration.getHeight());
        result.setUndecorated(frameConfiguration.isDistractionFree());
        result.setAlwaysOnTop(frameConfiguration.isAlwaysOnTop());
        result.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        // Watch for user closing frames and notify listeners
        result.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                for (Function<PhotoFrame, Void> disposeListener : disposeListeners) {
                    disposeListener.apply(PhotoFrame.this);
                }
            }
        });
        // Watch for frame move/resize to update frame state
        result.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                frameState.getNormalConfig().setWidth(theFrame.getWidth());
                frameState.getNormalConfig().setHeight(theFrame.getHeight());
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                frameState.getNormalConfig().setX(theFrame.getX());
                frameState.getNormalConfig().setY(theFrame.getY());
            }
        });
//        controlPanel.setVisible(!frameConfiguration.isDistractionFree());
        rowsField.setText(Integer.toString(frameConfiguration.getRows()));
        columnsField.setText(Integer.toString(frameConfiguration.getColumns()));
/*
        result.addComponentListener(new ComponentAdapter() {
            // Get the panels set up the first time this frame is shown, but I don't think it needs to
            // happen after that.
            // TODO: I think this is wrong. I've started getting a blank, gray frame at startup and after
            // going "distraction free" on linux, at least. I think it's because the setVisible() call over
            // in Application happens first, and then this fires to add the panels.
            @Override
            public void componentShown(ComponentEvent e) {
                wranglePanels();
                result.removeComponentListener(this);
            }
        });
*/
/*
        result.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                currentFrameConfiguration()
                        .setX(theFrame.getX())
                        .setY(theFrame.getY())
                        .setWidth(theFrame.getWidth())
                        .setHeight(theFrame.getHeight());
                newPanelSize();
            }

            @Override
            public void componentShown(ComponentEvent e) {
                newPanelSize();
            }
        });
*/
        if (this.frameState.isFullScreen()) {
            GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice device = env.getDefaultScreenDevice();
            device.setFullScreenWindow(result);
        }
        return result;
    }

    public void show() {
        theFrame.setVisible(true);
    }

    public void onDispose(Function<PhotoFrame, Void> f) {
        disposeListeners.add(f);
    }

    public Collection<PhotoPanel> getPanels() {
        return List.of(photoPanel);
    }
    // Note to self: The generated form code should appear down here at the bottom of the file, but you have to enable
    // that in Settings under GUI Designer. It defaults to creating binary output, which wouldn't carry over to a gradle
    // build (I think). In addition, you have to turn off Gradle as the build/run engine, also in Settings under Gradle.
    // Otherwise, IDEA won't generate the code. It only does it when it's building/running the project itself.

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        controlPanel = new JPanel();
        controlPanel.setLayout(new GridLayoutManager(2, 9, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(controlPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("New Frame");
        controlPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        controlPanel.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        newFrameButton = new JButton();
        newFrameButton.setText("+");
        controlPanel.add(newFrameButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        controlPanel.add(spacer2, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        lessColumnsButton = new JButton();
        lessColumnsButton.setText("-");
        controlPanel.add(lessColumnsButton, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        columnsField = new JTextField();
        columnsField.setHorizontalAlignment(0);
        controlPanel.add(columnsField, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(25, -1), null, 0, false));
        moreColumnsButton = new JButton();
        moreColumnsButton.setText("+");
        controlPanel.add(moreColumnsButton, new GridConstraints(1, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lessRowsButton = new JButton();
        lessRowsButton.setText("-");
        controlPanel.add(lessRowsButton, new GridConstraints(1, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rowsField = new JTextField();
        rowsField.setHorizontalAlignment(0);
        controlPanel.add(rowsField, new GridConstraints(1, 6, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(25, -1), null, 1, false));
        moreRowsButton = new JButton();
        moreRowsButton.setText("+");
        controlPanel.add(moreRowsButton, new GridConstraints(1, 7, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        controlPanel.add(spacer3, new GridConstraints(1, 8, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Columns");
        controlPanel.add(label2, new GridConstraints(0, 2, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Rows");
        controlPanel.add(label3, new GridConstraints(0, 5, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        controlPanel.add(spacer4, new GridConstraints(0, 8, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        mainDisplayArea = new JPanel();
        mainDisplayArea.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(mainDisplayArea, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
