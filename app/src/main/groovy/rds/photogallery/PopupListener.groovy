package rds.photogallery

import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.Lists
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.swing.*
import java.awt.*
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.List

class PopupListener extends MouseAdapter {
    private static final Logger log = LoggerFactory.getLogger(PhotoPanel.class)

    private PhotoPanel photoPanel
//    private PhotosController controller
//    private PhotoDataSource photoDataSource
    private JMenu ratingsMenu
    private JMenuItem rateLowerMenuItem
    private JMenuItem rateHigherMenuItem
    private JMenuItem removeMenuItem
    private JTextField tagsField = new JTextField(30)

    PopupListener(PhotoPanel photoPanel) {
        this.photoPanel = photoPanel
        // The data source isn't yet created when the listener is instantiated. Have to get it a different way.
//        this.photoDataSource = photoDataSource
//        this.controller = controller
    }

    void mousePressed(MouseEvent e) {
        maybeShowPopup(e)
    }

    void mouseReleased(MouseEvent e) {
        maybeShowPopup(e)
    }

    private void maybeShowPopup(MouseEvent e) {
        final CompletePhoto photo = photoPanel.photoOnDisplay
        final PhotoData photoData = photo.data
        if (e.isPopupTrigger()) {
            JPopupMenu menu = buildPopupMenu(photo)
            String menuText
            String tagsText
            rateLowerMenuItem.setEnabled(true)
            rateHigherMenuItem.setEnabled(true)
            if (photoData == null || photoData.getRating() == PhotoData.UNRATED) {
                rateLowerMenuItem.setEnabled(false)
                rateHigherMenuItem.setEnabled(false)
                menuText = "Rate it"
                tagsText = photoData == null ? "" : Joiner.on(", ").join(photoData.getUserTags())
            } else {
                tagsText = Joiner.on(", ").join(photoData.getUserTags())
                if (photoData.getRating() == 0) {
                    menuText = "X"
                    rateLowerMenuItem.setEnabled(false)
                    removeMenuItem.setEnabled(true)
                } else {
                    char[] stars = new char[photoData.getRating()]
                    Arrays.fill(stars, '*' as char)
                    menuText = new String(stars)
                    if (photoData.getRating() == 1) {
                        rateLowerMenuItem.setEnabled(false)
                    }
                    if (photoData.getRating() == 5) {
                        rateHigherMenuItem.setEnabled(false)
                    }
                }
            }
            ratingsMenu.setText(menuText)
            tagsField.setText(tagsText)
            menu.show(photoPanel, e.getX(), e.getY())
        }
    }

    private JPopupMenu buildPopupMenu(CompletePhoto photo) {
        final PhotoData photoData = photo.data
        JPopupMenu contextMenu = new JPopupMenu()
        JMenuItem makeStickyMenuItem = new JMenuItem("Toggle sticky")
        makeStickyMenuItem.addActionListener(new ToggleStickyListener())
        JMenuItem viewLargerMenuItem = new JMenuItem("Full screen")
//        viewLargerMenuItem.addActionListener(new FullScreenListener(photoData, photoPanel.getCurrentlyDisplayedImage()))
        JMenuItem forceNextMenuItem = new JMenuItem("Force next")
        forceNextMenuItem.addActionListener(new ForceChangePhotoListener(Direction.NEXT))
        JMenuItem forcePrevMenuItem = new JMenuItem("Force previous")
        forcePrevMenuItem.addActionListener(new ForceChangePhotoListener(Direction.PREVIOUS))
        JMenuItem noStars = new JMenuItem("X")
        noStars.addActionListener(new RatingActionListener(photoData))
        JMenuItem oneStar = new JMenuItem("*")
        oneStar.addActionListener(new RatingActionListener(photoData))
        JMenuItem twoStars = new JMenuItem("**")
        twoStars.addActionListener(new RatingActionListener(photoData))
        JMenuItem threeStars = new JMenuItem("***")
        threeStars.addActionListener(new RatingActionListener(photoData))
        JMenuItem fourStars = new JMenuItem("****")
        fourStars.addActionListener(new RatingActionListener(photoData))
        JMenuItem fiveStars = new JMenuItem("*****")
        fiveStars.addActionListener(new RatingActionListener(photoData))
        rateLowerMenuItem = new JMenuItem("Lower rating")
        rateLowerMenuItem.addActionListener(new ModifyRatingListener(photoData, -1))
        rateHigherMenuItem = new JMenuItem("Raise rating")
        rateHigherMenuItem.addActionListener(new ModifyRatingListener(photoData, 1))
        removeMenuItem = new JMenuItem("Remove")
//        removeMenuItem.addActionListener(new RemovePhotoListener(photoData))
        removeMenuItem.setEnabled(false)
        ratingsMenu = new JMenu("Rate it")
        ratingsMenu.add(noStars)
        ratingsMenu.add(oneStar)
        ratingsMenu.add(twoStars)
        ratingsMenu.add(threeStars)
        ratingsMenu.add(fourStars)
        ratingsMenu.add(fiveStars)
        tagsField = new JTextField(30)
        tagsField.addActionListener(new SaveTagsListener(photoData))
        JButton saveTagsButton = new JButton("Save tags")
        saveTagsButton.addActionListener(new SaveTagsListener(photoData))
        JMenu tagsMenu = new JMenu("Tags")
        JPanel tagsPanel = new JPanel()
        tagsPanel.add(tagsField)
        tagsPanel.add(saveTagsButton)
        tagsMenu.add(tagsPanel)
        JMenuItem fileLocationMenuItem = new JMenuItem("Go to file")
        fileLocationMenuItem.addActionListener(new GoToFileLocationListener(photoData))
        JMenu pathMenu = new JMenu("File path")
        pathMenu.addMouseListener(new MouseAdapter() {
            @Override
            void mouseClicked(MouseEvent mouseEvent) {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
                Transferable transferable = new StringSelection(photoData.getPath())
                clipboard.setContents(transferable, null)
            }
        })
        JTextField fieldForPathMenu = new JTextField()
        fieldForPathMenu.setText(photoData.getPath())
        fieldForPathMenu.setEditable(false)
        fieldForPathMenu.addMouseListener(new MouseAdapter() {
            @Override
            void mouseClicked(MouseEvent mouseEvent) {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
                Transferable transferable = new StringSelection(photoData.getPath())
                clipboard.setContents(transferable, null)
            }
        })
        JMenuItem gimpIt = new JMenuItem("Gimp it")
        gimpIt.addActionListener(e -> {
            String photoPath = App.instance.resolvePhotoPath(photoData).getAbsolutePath()
            try {
                Runtime.getRuntime().exec("gimp " + photoPath)
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to start gimp", ex)
            }
        })
        pathMenu.add(fieldForPathMenu)
        contextMenu.add(makeStickyMenuItem)
        contextMenu.add(viewLargerMenuItem)
        contextMenu.add(forceNextMenuItem)
        contextMenu.add(forcePrevMenuItem)
        contextMenu.add(rateLowerMenuItem)
        contextMenu.add(ratingsMenu)
        contextMenu.add(rateHigherMenuItem)
        contextMenu.add(removeMenuItem)
        contextMenu.add(tagsMenu)
        contextMenu.add(fileLocationMenuItem)
        contextMenu.add(pathMenu)
        contextMenu.add(gimpIt)
        hackToLetTagsFieldHandleKeypresses(tagsField)
        return contextMenu
    }

    static void hackToLetTagsFieldHandleKeypresses(JTextField textField) {
        // I can't figure out another way to keep the global hotkeys from firing when I type in the text field.
        // This basically overrides the global bindings with local ones that do nothing.
        ActionListener noop = e -> {
        }
        for (char c = 'A'; c < (char) 'Z'; c++) {
            String keystroke = new String(new char[]{c})
            textField.registerKeyboardAction(noop, KeyStroke.getKeyStroke(keystroke), JComponent.WHEN_FOCUSED)
        }
        for (char c = '0'; c < (char) '9'; c++) {
            String keystroke = new String(new char[]{c})
            textField.registerKeyboardAction(noop, KeyStroke.getKeyStroke(keystroke), JComponent.WHEN_FOCUSED)
        }
        textField.registerKeyboardAction(noop, KeyStroke.getKeyStroke(" "), JComponent.WHEN_FOCUSED)
        textField.registerKeyboardAction(noop, KeyStroke.getKeyStroke(","), JComponent.WHEN_FOCUSED)
    }

    private class RatingActionListener implements ActionListener {
        private final PhotoData photoData

        RatingActionListener(PhotoData photoData) {
            this.photoData = photoData
        }

        @Override
        void actionPerformed(ActionEvent e) {
            int stars
            if (e.getActionCommand() == "X") {
                stars = 0
            } else {
                stars = e.getActionCommand().length()
            }
            App.instance.localData.changeRating(photoData, stars)
            photoPanel.repaint()
        }
    }

    private class ModifyRatingListener implements ActionListener {
        private final PhotoData photoData
        private int modification

        ModifyRatingListener(PhotoData photoData, int modification) {
            this.photoData = photoData
            this.modification = modification
        }

        @Override
        void actionPerformed(ActionEvent e) {
            int stars = photoData.getRating() + modification
            // This is a quick hack to get the data we need. What's a better way to ensure this listener can reach the
            // critical objects, like the data source?
            App.instance.localData.changeRating(photoData, stars)
            photoPanel.repaint()
        }
    }

    private class SaveTagsListener implements ActionListener {
        private final PhotoData data

        SaveTagsListener(PhotoData data) {
            this.data = data
        }

        @Override
        void actionPerformed(ActionEvent e) {
            String tagString = tagsField.getText()
            log.info("Setting tags to {}", tagString)
            data.setUserTags(Lists.newArrayList(Splitter.on(",").trimResults().omitEmptyStrings().split(tagString)))
            photoPanel.repaint()
        }
    }

    private class GoToFileLocationListener implements ActionListener {
        private PhotoData photoData

        GoToFileLocationListener(PhotoData photoData) {
            this.photoData = photoData
        }

        @Override
        void actionPerformed(ActionEvent e) {
            def file = App.instance.resolvePhotoPath(photoData)
            // Cheap check for OS by looking for backslash in the path. I'm only supporting windows and ubuntu here!
            def cwd = System.getProperty('user.dir')
            if (cwd.contains('\\')) {
                Runtime.getRuntime().exec("explorer /select," + file.getAbsolutePath())
            } else  {
                String[] cmd = ["nautilus", file.getAbsolutePath()]
                Runtime.getRuntime().exec(cmd)
            }
        }
    }

    private class ToggleStickyListener implements ActionListener {
        @Override
        void actionPerformed(ActionEvent e) {
            controller.toggleSticky(photoPanel)
        }
    }

    enum Direction { NEXT, PREVIOUS }

    private class ForceChangePhotoListener implements ActionListener {
        private final Direction direction

        ForceChangePhotoListener(Direction direction) {
            this.direction = direction
        }

        @Override
        void actionPerformed(ActionEvent actionEvent) {
            switch (direction) {
                case Direction.NEXT:
                    controller.nextPhotoEvenIfStickyFor(photoPanel)
                    break
                case Direction.PREVIOUS:
                    controller.previousPhotoEvenIfStickyFor(photoPanel)
                    break
            }
        }
    }

/*
    private class RemovePhotoListener implements ActionListener {
        private final PhotoData photoData

        RemovePhotoListener(PhotoData photoData) {
            this.photoData = photoData
        }

        @Override
        void actionPerformed(ActionEvent e) {
            String message = "This will remove the photo from both the database and the disk. Are you sure?"
            int answer = JOptionPane.showConfirmDialog(ratingsMenu, message, "Really remove?", JOptionPane.YES_NO_OPTION)
            if (answer == JOptionPane.YES_OPTION) {
                File photoFile = Application.instance.resolvePhotoPath(photoData)
                // Force hash population so the database can deal with this thing in the future.
                // This is totally not the right abstraction, but without this, saving the db can fail
                // after removing a photo during normal operations. Probably some other thing should be
                // responsible for removing the photo, like the db itself, so that removing it from the
                // db and physically from the disk can be the same, logical operation. That's more or less
                // why I made the PhotoDatabasePlus, after all.
                photoData.getPhotoHash()
                Application.instance.removePhotoData(photoData.getRelativePath())
                try {
                    // Might be doing a db-only remove, so don't try to delete if the file doesn't exist.
                    if (Files.exists(photoFile.toPath())) {
                        Files.delete(photoFile.toPath())
                    }
                } catch (IOException e1) {
                    throw new RuntimeException("Couldn't delete photo file. Maybe it's currently being loaded?", e1)
                }
            }
        }
    }
*/
}
