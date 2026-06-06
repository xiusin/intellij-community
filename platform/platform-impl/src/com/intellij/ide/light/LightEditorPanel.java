// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.light;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.*;
import java.nio.file.*;

public class LightEditorPanel extends JPanel {
  private final JTabbedPane tabbedPane;
  private final JTextArea currentEditor;
  private File currentFile;

  public LightEditorPanel() {
    setLayout(new BorderLayout());
    tabbedPane = new JTabbedPane();
    currentEditor = new JTextArea();
    currentEditor.setFont(new Font("Monospaced", Font.PLAIN, 14));
    currentEditor.setTabSize(4);
    currentEditor.setLineWrap(false);
    currentEditor.getDocument().addDocumentListener(new DocumentListener() {
      public void changedUpdate(DocumentEvent e) { setModified(); }
      public void removeUpdate(DocumentEvent e) { setModified(); }
      public void insertUpdate(DocumentEvent e) { setModified(); }
      private void setModified() {
        if (currentFile != null) {
          tabbedPane.setTitleAt(tabbedPane.getSelectedIndex(), "*" + currentFile.getName());
        }
      }
    });

    JScrollPane scrollPane = new JScrollPane(currentEditor);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    tabbedPane.addTab("Untitled", scrollPane);
    add(tabbedPane, BorderLayout.CENTER);
  }

  public void newFile() {
    currentFile = null;
    currentEditor.setText("");
    tabbedPane.setTitleAt(tabbedPane.getSelectedIndex(), "Untitled");
  }

  public void openFile() {
    JFileChooser chooser = new JFileChooser();
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      try {
        currentFile = chooser.getSelectedFile();
        String content = new String(Files.readAllBytes(currentFile.toPath()));
        currentEditor.setText(content);
        tabbedPane.setTitleAt(tabbedPane.getSelectedIndex(), currentFile.getName());
        currentEditor.setCaretPosition(0);
      }
      catch (IOException ex) {
        JOptionPane.showMessageDialog(this, "Error opening file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public void saveFile() {
    if (currentFile == null) {
      saveFileAs();
      return;
    }
    try {
      Files.write(currentFile.toPath(), currentEditor.getText().getBytes());
      tabbedPane.setTitleAt(tabbedPane.getSelectedIndex(), currentFile.getName());
    }
    catch (IOException ex) {
      JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  public void saveFileAs() {
    JFileChooser chooser = new JFileChooser();
    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      currentFile = chooser.getSelectedFile();
      saveFile();
    }
  }
}