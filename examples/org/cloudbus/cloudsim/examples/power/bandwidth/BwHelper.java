package org.cloudbus.cloudsim.examples.power.bandwidth;


import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.VmSchedulerTimeSharedOverSubscription;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.examples.power.Constants;
import org.cloudbus.cloudsim.examples.power.Helper;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.PowerHostUtilizationHistory;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.Vm;


public class BwHelper extends Helper 
{
	private static int[] bandwidthList;

    public final static boolean transmission = true;
    public final static boolean writeFile = true;
    public final static String outputMigrationFileName = "migrationResult.txt";
    public static BufferedWriter outputMigrationFile = null; 
    public final static int simlationTime = 20;
    public final static int overPSOThreshold = 1000;
    public final static int underPSOThreshold = 1000;
    public final static double THRESHOLD = 1;
    public final static boolean VMIncrease = false;
    public final static boolean HostSort = true;    
    public final static boolean BestFitHost = false;
    public final static boolean BestFitVM = false;
    public final static boolean AllData = false;
    public static int dimension;
    public static List<Host> oldHostList;
    public static double totalTransmisionEnergy = 0;
    public final static double LOW_BW = 80000000;
    public final static double HIGH_BW = 140000000;
    public final static double LOW_POWER = 4000;
    public final static double HIGH_POWER = 6000;
    
	
	private static void createBWList(int hostsNumber)
	{
		bandwidthList = new int[hostsNumber];
		for(int i = 0; i < hostsNumber; i++)
		{
			bandwidthList[i] = (int) LOW_BW + 3000000 * (i % 20);
		}
	}
	
	private static double transmissionEnergyModelCal(double oldBw, double newBw, int ram){
        double result = 0;
        double powerRatio = (HIGH_POWER - LOW_POWER) / (HIGH_BW - LOW_BW);
        result = (ram * 8000)/oldBw * (LOW_POWER + (oldBw - LOW_BW) * powerRatio)
                + (ram * 8000)/newBw * (LOW_POWER +(newBw- LOW_BW) * powerRatio);
        return result;
    }

    public static double getTransmissionPower(PowerHost oldHost, PowerHost newHost, Vm vm){
        double result = 0;
        long oldBandwidth = oldHost.getBw();
        long newBandwidth = newHost.getBw();
        int ram = vm.getRam();
        result = transmissionEnergyModelCal(oldBandwidth, newBandwidth, ram);
        return result;
    }
	
	//@override;
	public static List<PowerHost> createHostList(int hostsNumber) {
		createBWList(hostsNumber);
		List<PowerHost> hostList = new ArrayList<PowerHost>();
		for (int i = 0; i < hostsNumber; i++) {
			int hostType = i % Constants.HOST_TYPES;

			List<Pe> peList = new ArrayList<Pe>();
			for (int j = 0; j < Constants.HOST_PES[hostType]; j++) {
				peList.add(new Pe(j, new PeProvisionerSimple(Constants.HOST_MIPS[hostType])));
			}

			hostList.add(new PowerHostUtilizationHistory(
					i,
					new RamProvisionerSimple(Constants.HOST_RAM[hostType]),
					new BwProvisionerSimple(bandwidthList[i]),
					Constants.HOST_STORAGE,
					peList,
					new VmSchedulerTimeSharedOverSubscription(peList),
					Constants.HOST_POWER[hostType]));
		}
		return hostList;
	}
	
	
}
