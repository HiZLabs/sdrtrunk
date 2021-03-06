/*******************************************************************************
 * sdrtrunk
 * Copyright (C) 2014-2017 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 ******************************************************************************/
package gui;

import alias.AliasModel;
import alias.action.AliasActionManager;
import audio.AudioManager;
import audio.broadcast.BroadcastModel;
import audio.broadcast.BroadcastStatusPanel;
import com.jidesoft.swing.JideSplitPane;
import controller.ControllerPanel;
import controller.channel.ChannelModel;
import controller.channel.ChannelProcessingManager;
import controller.channel.ChannelSelectionManager;
import controller.channel.map.ChannelMapModel;
import icon.IconManager;
import map.MapService;
import module.log.EventLogManager;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import playlist.PlaylistManager;
import properties.SystemProperties;
import record.RecorderManager;
import sample.Listener;
import settings.SettingsManager;
import source.SourceManager;
import source.tuner.TunerEvent;
import source.tuner.TunerModel;
import source.tuner.TunerSpectralDisplayManager;
import source.tuner.configuration.TunerConfigurationModel;
import spectrum.SpectralDisplayPanel;
import util.ThreadPool;
import util.TimeStamp;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class SDRTrunk implements Listener<TunerEvent>
{
    private final static Logger mLog = LoggerFactory.getLogger(SDRTrunk.class);
    private static final String PROPERTY_BROADCAST_STATUS_VISIBLE = "main.broadcast.status.visible";
    private boolean mBroadcastStatusVisible;

    private IconManager mIconManager;
    private BroadcastStatusPanel mBroadcastStatusPanel;
    private BroadcastModel mBroadcastModel;
    private ControllerPanel mControllerPanel;
    private SettingsManager mSettingsManager;
    private SpectralDisplayPanel mSpectralPanel;
    private JFrame mMainGui = new JFrame();
    private JideSplitPane mSplitPane;

    private String mTitle;

    public SDRTrunk()
    {
        mLog.info("");
        mLog.info("*******************************************************************");
        mLog.info("**** sdrtrunk: a trunked radio and digital decoding application ***");
        mLog.info("****  website: https://github.com/dsheirer/sdrtrunk             ***");
        mLog.info("*******************************************************************");
        mLog.info("");
        mLog.info("Host CPU Cores: " + Runtime.getRuntime().availableProcessors());
        mLog.info("Host Memory Total: " + Runtime.getRuntime().totalMemory());
        mLog.info("Host Memory Max: " + Runtime.getRuntime().maxMemory());
        mLog.info("Host Memory Free: " + Runtime.getRuntime().freeMemory());

        //Setup the application home directory
        Path home = getHomePath();

        ThreadPool.logSettings();

        mLog.info("Home path: " + home.toString());

        //Load properties file
        if(home != null)
        {
            loadProperties(home);
        }

        //Log current properties setting
        SystemProperties.getInstance().logCurrentSettings();

        TunerConfigurationModel tunerConfigurationModel = new TunerConfigurationModel();
        TunerModel tunerModel = new TunerModel(tunerConfigurationModel);

        mIconManager = new IconManager();

        mSettingsManager = new SettingsManager(tunerConfigurationModel);

        AliasModel aliasModel = new AliasModel();

        ChannelModel channelModel = new ChannelModel();

        ChannelMapModel channelMapModel = new ChannelMapModel();

        EventLogManager eventLogManager = new EventLogManager();

        RecorderManager recorderManager = new RecorderManager();

        SourceManager sourceManager = new SourceManager(tunerModel, mSettingsManager);

        ChannelProcessingManager channelProcessingManager = new ChannelProcessingManager(
            channelModel, channelMapModel, aliasModel, eventLogManager, recorderManager, sourceManager);
        channelProcessingManager.addAudioPacketListener(recorderManager);

        channelModel.addListener(channelProcessingManager);

        ChannelSelectionManager channelSelectionManager =
            new ChannelSelectionManager(channelModel);
        channelModel.addListener(channelSelectionManager);

        AliasActionManager aliasActionManager = new AliasActionManager();
        channelProcessingManager.addMessageListener(aliasActionManager);

        AudioManager audioManager = new AudioManager(sourceManager.getMixerManager());
        channelProcessingManager.addAudioPacketListener(audioManager);

        mBroadcastModel = new BroadcastModel(mIconManager);

        channelProcessingManager.addAudioPacketListener(mBroadcastModel);

        MapService mapService = new MapService(mIconManager);
        channelProcessingManager.addMessageListener(mapService);

        mControllerPanel = new ControllerPanel(audioManager, aliasModel, mBroadcastModel,
            channelModel, channelMapModel, channelProcessingManager, mIconManager,
            mapService, mSettingsManager, sourceManager, tunerModel);

        mSpectralPanel = new SpectralDisplayPanel(channelModel,
            channelProcessingManager, mSettingsManager);

        TunerSpectralDisplayManager tunerSpectralDisplayManager =
            new TunerSpectralDisplayManager(mSpectralPanel,
                channelModel, channelProcessingManager, mSettingsManager);
        tunerModel.addListener(tunerSpectralDisplayManager);
        tunerModel.addListener(this);

        PlaylistManager playlistManager = new PlaylistManager(aliasModel, mBroadcastModel, channelModel,
            channelMapModel);

        playlistManager.init();

        mLog.info("starting main application gui");

        //Initialize the GUI
        initGUI();

        tunerModel.requestFirstTunerDisplay();

        //Start the gui
        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                try
                {
                    mMainGui.setVisible(true);
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Launch the application.
     */
    public static void main(String[] args)
    {
        new SDRTrunk();
    }

    /**
     * Initialize the contents of the frame.
     */
    private void initGUI()
    {
        mMainGui.setLayout(new MigLayout("insets 0 0 0 0 ", "[grow,fill]", "[grow,fill]"));

        /**
         * Setup main JFrame window
         */
        mTitle = SystemProperties.getInstance().getApplicationName();
        mMainGui.setTitle(mTitle);
        mMainGui.setBounds(100, 100, 1280, 800);
        mMainGui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Set preferred sizes to influence the split
        mSpectralPanel.setPreferredSize(new Dimension(1280, 300));
        mControllerPanel.setPreferredSize(new Dimension(1280, 500));

        mSplitPane = new JideSplitPane(JideSplitPane.VERTICAL_SPLIT);
        mSplitPane.setDividerSize(5);
        mSplitPane.add(mSpectralPanel);
        mSplitPane.add(mControllerPanel);

        mBroadcastStatusVisible = SystemProperties.getInstance().get(PROPERTY_BROADCAST_STATUS_VISIBLE, false);

        //Show broadcast status panel when user requests - disabled by default
        if(mBroadcastStatusVisible)
        {
            mSplitPane.add(getBroadcastStatusPanel());
        }

        mMainGui.add(mSplitPane, "cell 0 0,span,grow");

        /**
         * Menu items
         */
        JMenuBar menuBar = new JMenuBar();
        mMainGui.setJMenuBar(menuBar);

        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);

        JMenuItem logFilesMenu = new JMenuItem("Logs & Recordings");
        logFilesMenu.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent arg0)
            {
                try
                {
                    Desktop.getDesktop().open(getHomePath().toFile());
                }
                catch(Exception e)
                {
                    mLog.error("Couldn't open file explorer");

                    JOptionPane.showMessageDialog(mMainGui,
                        "Can't launch file explorer - files are located at: " +
                            getHomePath().toString(),
                        "Can't launch file explorer",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        fileMenu.add(logFilesMenu);

        JMenuItem settingsMenu = new JMenuItem("Icon Manager");
        settingsMenu.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent arg0)
            {
                mIconManager.showEditor(mMainGui);
            }
        });
        fileMenu.add(settingsMenu);

        fileMenu.add(new JSeparator());

        JMenuItem exitMenu = new JMenuItem("Exit");
        exitMenu.addActionListener(
            new ActionListener()
            {
                public void actionPerformed(ActionEvent event)
                {
                    System.exit(0);
                }
            }
        );

        fileMenu.add(exitMenu);

        JMenu viewMenu = new JMenu("View");

        viewMenu.add(new BroadcastStatusVisibleMenuItem(mControllerPanel));

        menuBar.add(viewMenu);

        JMenuItem screenCaptureItem = new JMenuItem("Screen Capture");

        screenCaptureItem.setMnemonic(KeyEvent.VK_C);
        screenCaptureItem.setAccelerator(
            KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.ALT_MASK));

        screenCaptureItem.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent arg0)
            {
                try
                {
                    Robot robot = new Robot();

                    final BufferedImage image =
                        robot.createScreenCapture(mMainGui.getBounds());

                    SystemProperties props = SystemProperties.getInstance();

                    Path capturePath = props.getApplicationFolder("screen_captures");

                    if(!Files.exists(capturePath))
                    {
                        try
                        {
                            Files.createDirectory(capturePath);
                        }
                        catch(IOException e)
                        {
                            mLog.error("Couldn't create 'screen_captures' "
                                + "subdirectory in the " +
                                "SDRTrunk application directory", e);
                        }
                    }

                    String filename = TimeStamp.getTimeStamp("_") +
                        "_screen_capture.png";

                    final Path captureFile = capturePath.resolve(filename);

                    EventQueue.invokeLater(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            try
                            {
                                ImageIO.write(image, "png",
                                    captureFile.toFile());
                            }
                            catch(IOException e)
                            {
                                mLog.error("Couldn't write screen capture to "
                                    + "file [" + captureFile.toString() + "]", e);
                            }
                        }
                    });
                }
                catch(AWTException e)
                {
                    mLog.error("Exception while taking screen capture", e);
                }
            }
        });

        menuBar.add(screenCaptureItem);
    }

    /**
     * Lazy constructor for broadcast status panel
     */
    private BroadcastStatusPanel getBroadcastStatusPanel()
    {
        if(mBroadcastStatusPanel == null)
        {
            mBroadcastStatusPanel = new BroadcastStatusPanel(mBroadcastModel);
            mBroadcastStatusPanel.setPreferredSize(new Dimension(880, 70));
            mBroadcastStatusPanel.getTable().setEnabled(false);
        }

        return mBroadcastStatusPanel;
    }

    /**
     * Toggles visibility of the broadcast channels status panel at the bottom of the controller panel
     */
    private void toggleBroadcastStatusPanelVisibility()
    {
        mBroadcastStatusVisible = !mBroadcastStatusVisible;

        EventQueue.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                if(mBroadcastStatusVisible)
                {
                    mSplitPane.add(getBroadcastStatusPanel());
                }
                else
                {
                    mSplitPane.remove(getBroadcastStatusPanel());
                }

                mMainGui.revalidate();
            }
        });

        SystemProperties.getInstance().set(PROPERTY_BROADCAST_STATUS_VISIBLE, mBroadcastStatusVisible);
    }


    /**
     * Loads the application properties file from the user's home directory,
     * creating the properties file for the first-time, if necessary
     */
    private void loadProperties(Path homePath)
    {
        Path propsPath = homePath.resolve("SDRTrunk.properties");

        if(!Files.exists(propsPath))
        {
            try
            {
                mLog.info("SDRTrunk - creating application properties file [" +
                    propsPath.toAbsolutePath() + "]");

                Files.createFile(propsPath);
            }
            catch(IOException e)
            {
                mLog.error("SDRTrunk - couldn't create application properties "
                    + "file [" + propsPath.toAbsolutePath(), e);
            }
        }

        if(Files.exists(propsPath))
        {
            SystemProperties.getInstance().load(propsPath);
        }
        else
        {
            mLog.error("SDRTrunk - couldn't find or recreate the SDRTrunk " +
                "application properties file");
        }
    }

    /**
     * Gets (or creates) the SDRTRunk application home directory.
     *
     * Note: the user can change this setting to allow log files and other
     * files to reside elsewhere on the file system.
     */
    private Path getHomePath()
    {
        Path homePath = FileSystems.getDefault()
            .getPath(System.getProperty("user.home"), "SDRTrunk");

        if(!Files.exists(homePath))
        {
            try
            {
                Files.createDirectory(homePath);

                mLog.info("SDRTrunk - created application home directory [" +
                    homePath.toString() + "]");
            }
            catch(Exception e)
            {
                homePath = null;

                mLog.error("SDRTrunk: exception while creating SDRTrunk home " +
                    "directory in the user's home directory", e);
            }
        }

        return homePath;
    }

    @Override
    public void receive(TunerEvent event)
    {
        if(event.getEvent() == TunerEvent.Event.REQUEST_MAIN_SPECTRAL_DISPLAY)
        {
            mMainGui.setTitle(mTitle + " - " + event.getTuner().getName());
        }
    }

    public class BroadcastStatusVisibleMenuItem extends JCheckBoxMenuItem
    {
        private ControllerPanel mControllerPanel;

        public BroadcastStatusVisibleMenuItem(ControllerPanel controllerPanel)
        {
            super("Show Streaming Status");

            mControllerPanel = controllerPanel;

            setSelected(mBroadcastStatusPanel != null);

            addActionListener(new ActionListener()
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    toggleBroadcastStatusPanelVisibility();
                    setSelected(mBroadcastStatusVisible);
                }
            });
        }
    }
}
