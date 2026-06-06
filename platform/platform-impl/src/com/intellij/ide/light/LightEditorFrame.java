// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.light;

import com.intellij.ide.LightModeServiceImpl;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class LightEditorFrame extends JFrame {
  private final LightEditorPanel editorPanel;
  private final JLabel statusLabel;

  public LightEditorFrame() {
    super("Light Editor");
    setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    setSize(900, 600);
    setLocationRelativeTo(null);

    editorPanel = new LightEditorPanel();
    statusLabel = new JLabel("Ready");

    setupMenuBar();
    setupToolBar();

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(editorPanel, BorderLayout.CENTER);
    panel.add(statusLabel, BorderLayout.SOUTH);

    setContentPane(panel);
  }

  private void setupMenuBar() {
    JMenuBar menuBar = new JMenuBar();
    JMenu fileMenu = new JMenu("File");
    fileMenu.add(createMenuItem("New", KeyEvent.VK_N));
    fileMenu.add(createMenuItem("Open...", KeyEvent.VK_O));
    fileMenu.add(createMenuItem("Save", KeyEvent.VK_S));
    fileMenu.add(createMenuItem("Save As...", KeyEvent.VK_S, true));
    fileMenu.addSeparator();
    fileMenu.add(createMenuItem("Switch to Full Mode", -1));
    fileMenu.addSeparator();
    fileMenu.add(createMenuItem("Exit", KeyEvent.VK_Q));
    menuBar.add(fileMenu);
    setJMenuBar(menuBar);
  }

  private JMenuItem createMenuItem(String name, int keyCode) {
    return createMenuItem(name, keyCode, false);
  }

  private JMenuItem createMenuItem(String name, int keyCode, boolean shift) {
    JMenuItem item = new JMenuItem(name);
    if (keyCode > 0) {
      item.setAccelerator(KeyStroke.getKeyStroke(keyCode,
        Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | (shift ? InputEvent.SHIFT_DOWN_MASK : 0)));
    }
    if (name.equals("Save")) {
      item.addActionListener(e -> editorPanel.saveFile());
    }
    else if (name.equals("Open...")) {
      item.addActionListener(e -> editorPanel.openFile());
    }
    else if (name.equals("New")) {
      item.addActionListener(e -> editorPanel.newFile());
    }
    else if (name.equals("Save As...")) {
      item.addActionListener(e -> editorPanel.saveFileAs());
    }
    else if (name.equals("Switch to Full Mode")) {
      item.addActionListener(e -> switchToFullMode());
    }
    else if (name.equals("Exit")) {
      item.addActionListener(e -> dispose());
    }
    return item;
  }

  private void setupToolBar() {
    JToolBar toolBar = new JToolBar();
    toolBar.add(createToolButton("New", () -> editorPanel.newFile()));
    toolBar.add(createToolButton("Open", () -> editorPanel.openFile()));
    toolBar.add(createToolButton("Save", () -> editorPanel.saveFile()));
    toolBar.addSeparator();
    JButton fullModeBtn = new JButton("Switch to Full Mode");
    fullModeBtn.addActionListener(e -> switchToFullMode());
    toolBar.add(fullModeBtn);
    add(toolBar, BorderLayout.NORTH);
  }

  private JButton createToolButton(String text, Runnable action) {
    JButton btn = new JButton(text);
    btn.addActionListener(e -> action.run());
    return btn;
  }

  private void switchToFullMode() {
    int result = JOptionPane.showConfirmDialog(this,
      "Switch to full IDE mode? This will restart with full project capabilities.",
      "Switch Mode", JOptionPane.OK_CANCEL_OPTION);
    if (result == JOptionPane.OK_OPTION) {
      LightModeServiceImpl.lightModeRequested = false;
      dispose();
      System.exit(0);
    }
  }
}