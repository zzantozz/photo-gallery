package rds.photogallery

import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.WindowConstants
import javax.swing.border.EmptyBorder
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.CountDownLatch

/**
 * Provides a standalone dialog. This works a lot like a JOptionPane, but I've found that those aren't seen as a
 * "switch-to-able" window in Linux, so if the dialogs get behind another window, I just have to minimize things until I
 * find them. This will make dialogs that are full-fledged windows so that problem goes away. The methods here are
 * intentionally modelled after JOptionPane so switching between them is easy.
 */
class HomelessDialog {

    static void showMessageDialog(Component parentComponent, Object message) {
        if (parentComponent != null) {
            throw new RuntimeException(
                    "Hey, you're not homeless! Only use this if there's no parent component to attach a dialog to.")
        }
        JFrame frame = new JFrame("Message")
        frame.setLocation(400, 400)
        JPanel contentPane = new JPanel()
        frame.setContentPane(contentPane)
        contentPane.setBorder(new EmptyBorder(10, 10, 5, 10))
        contentPane.setLayout(new BorderLayout())
        CountDownLatch latch = new CountDownLatch(1)
        MessageReceivedListener messageReceivedListener = new MessageReceivedListener(frame, latch)
        addMessage(message, messageReceivedListener, frame, true)
        JPanel southPanel = new JPanel()
        JButton okButton = new JButton("OK")
        okButton.addActionListener(messageReceivedListener)
        southPanel.add(okButton)
        contentPane.add(southPanel, BorderLayout.SOUTH)
        frame.pack()
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
        frame.addWindowListener(new WindowAdapter() {
            @Override
            void windowClosing(WindowEvent e) {
                latch.countDown()
                frame.dispose()
            }
        })
        contentPane.registerKeyboardAction(messageReceivedListener, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
        frame.setVisible(true)
        try {
            latch.await()
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpected interrupt", e)
        }
    }

    private static void addMessage(Object message, ActionListener listener, JFrame frame, boolean first) {
        if (message instanceof String) {
            addMessage(new JLabel((String) message), listener, frame, first)
        } else if (message instanceof Component) {
            Component component = (Component) message
            tryListenerAdd(component, listener)
            if (first) {
                frame.getContentPane().add(component, BorderLayout.NORTH)
            } else {
                frame.getContentPane().add(component, BorderLayout.CENTER)
            }
        } else if (message instanceof Object[]) {
            boolean localFirst = first
            for (Object o : (Object[]) message) {
                addMessage(o, listener, frame, localFirst)
                localFirst = false
            }
        } else {
            throw new RuntimeException("I must've forgotten something that can be used as a message.")
        }
    }

    private static void tryListenerAdd(Component component, ActionListener listener) {
        if (component instanceof JPanel) {
            for (Component subcomponent : ((JPanel) component).getComponents()) {
                tryListenerAdd(subcomponent, listener)
            }
        }
        if (component instanceof JTextField)
            ((JTextField) component).addActionListener(listener)
        if (component instanceof JButton)
            ((JButton) component).addActionListener(listener)
        if (component instanceof JPasswordField)
            ((JPasswordField) component).addActionListener(listener)
    }

    static int showConfirmDialog(Component parentComponent, Object message, String title, int optionType) {
        if (parentComponent != null) {
            throw new RuntimeException(
                    "Hey, you're not homeless! Only use this if there's no parent component to attach a dialog to.")
        }
        if (optionType != JOptionPane.YES_NO_OPTION) {
            throw new IllegalStateException("I only know how to handle YES_NO_OPTION right now!")
        }
        JFrame frame = new JFrame("Exit now?")
        frame.setLocationRelativeTo(null)
        // This is the size of a typical JOptionPane dialog, as measured by xdotool. For some reason, this frame turns
        // out a little smaller than a JOptionPane in the height direction.
        frame.setMinimumSize(new Dimension(262, 90))
        JPanel contentPane = new JPanel()
        frame.setContentPane(contentPane)
        contentPane.setBorder(new EmptyBorder(10, 10, 5, 10))
        contentPane.setLayout(new BorderLayout())
        contentPane.add(new JLabel("Are you sure?"), BorderLayout.NORTH);
        JPanel southPanel = new JPanel()
        southPanel.setBorder(new EmptyBorder(10, 20, 5, 10))
        JButton yesButton = new JButton("Yes")
        yesButton.setMnemonic('Y')
        JButton noButton = new JButton("No")
        noButton.setMnemonic('N')
        southPanel.add(yesButton)
        southPanel.add(noButton)
        contentPane.add(southPanel, BorderLayout.SOUTH)
        // I found this code down deep under the JOptionPane stuff. If I try a simple CountDownLatch here, the dialog
        // seems to freeze, never showing any of its content. This makes it work. I don't know why.
        final EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue()
        def secondaryLoop = eventQueue.createSecondaryLoop()
        int returnValue = JOptionPane.CLOSED_OPTION
        contentPane.registerKeyboardAction((e) -> {
            returnValue = JOptionPane.YES_OPTION
            secondaryLoop.exit()
        }, KeyStroke.getKeyStroke('Y'), JComponent.WHEN_IN_FOCUSED_WINDOW)
        contentPane.registerKeyboardAction((e) -> {
            returnValue = JOptionPane.NO_OPTION
            secondaryLoop.exit()
        }, KeyStroke.getKeyStroke('N'), JComponent.WHEN_IN_FOCUSED_WINDOW)
        yesButton.addActionListener({
            returnValue = JOptionPane.YES_OPTION
            secondaryLoop.exit()
        })
        noButton.addActionListener({
            returnValue = JOptionPane.NO_OPTION
            secondaryLoop.exit()
        })
        frame.addWindowListener(new WindowAdapter() {
            @Override
            void windowClosing(WindowEvent e) {
                secondaryLoop.exit()
            }
        })
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
        frame.pack()
        frame.setVisible(true)
        secondaryLoop.enter()
        frame.dispose()
        returnValue
    }


    private static class MessageReceivedListener implements ActionListener {
        private JFrame frame
        private CountDownLatch latch

        MessageReceivedListener(JFrame frame, CountDownLatch latch) {
            this.frame = frame
            this.latch = latch
        }

        @Override
        void actionPerformed(ActionEvent e) {
            latch.countDown()
            frame.dispose()
        }
    }
}
