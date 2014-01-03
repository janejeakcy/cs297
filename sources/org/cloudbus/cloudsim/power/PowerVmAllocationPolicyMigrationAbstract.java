/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.power;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cloudbus.cloudsim.examples.power.bandwidth.BwHelper;
import org.cloudbus.cloudsim.examples.power.bandwidth.EnergyCalculator;
import org.cloudbus.cloudsim.examples.power.bandwidth.EnergyFitnessFunction;
import org.cloudbus.cloudsim.examples.power.bandwidth.EnergyParticle;
import net.sourceforge.jswarm_pso.Neighborhood;
import net.sourceforge.jswarm_pso.Neighborhood1D;
import net.sourceforge.jswarm_pso.Swarm;
import net.sourceforge.jswarm_pso.example_2.SwarmShow2D;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.HostDynamicWorkload;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.lists.PowerVmList;
import org.cloudbus.cloudsim.util.ExecutionTimeMeasurer;

/**
 * The class of an abstract power-aware VM allocation policy that dynamically optimizes the VM
 * allocation using migration.
 * 
 * If you are using any algorithms, policies or workload included in the power package, please cite
 * the following paper:
 * 
 * Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012
 * 
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 3.0
 */
public abstract class PowerVmAllocationPolicyMigrationAbstract extends PowerVmAllocationPolicyAbstract {


	/** The vm selection policy. */
	private PowerVmSelectionPolicy vmSelectionPolicy;

	/** The saved allocation. */
	private final List<Map<String, Object>> savedAllocation = new ArrayList<Map<String, Object>>();

	/** The utilization history. */
	private final Map<Integer, List<Double>> utilizationHistory = new HashMap<Integer, List<Double>>();

	/** The metric history. */
	private final Map<Integer, List<Double>> metricHistory = new HashMap<Integer, List<Double>>();

	/** The time history. */
	private final Map<Integer, List<Double>> timeHistory = new HashMap<Integer, List<Double>>();

	/** The execution time history vm selection. */
	private final List<Double> executionTimeHistoryVmSelection = new LinkedList<Double>();

	/** The execution time history host selection. */
	private final List<Double> executionTimeHistoryHostSelection = new LinkedList<Double>();

	/** The execution time history vm reallocation. */
	private final List<Double> executionTimeHistoryVmReallocation = new LinkedList<Double>();

	/** The execution time history total. */
	private final List<Double> executionTimeHistoryTotal = new LinkedList<Double>();

	/**
	 * Instantiates a new power vm allocation policy migration abstract.
	 * 
	 * @param hostList the host list
	 * @param vmSelectionPolicy the vm selection policy
	 */
	public PowerVmAllocationPolicyMigrationAbstract(
			List<? extends Host> hostList,
			PowerVmSelectionPolicy vmSelectionPolicy) {
		super(hostList);
		setVmSelectionPolicy(vmSelectionPolicy);
	}

	/**
	 * Optimize allocation of the VMs according to current utilization.
	 * 
	 * @param vmList the vm list
	 * 
	 * @return the array list< hash map< string, object>>
	 */
	@Override
	public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) {
		ExecutionTimeMeasurer.start("optimizeAllocationTotal");

		ExecutionTimeMeasurer.start("optimizeAllocationHostSelection");
		List<PowerHostUtilizationHistory> overUtilizedHosts = getOverUtilizedHosts();
		getExecutionTimeHistoryHostSelection().add(
				ExecutionTimeMeasurer.end("optimizeAllocationHostSelection"));

		printOverUtilizedHosts(overUtilizedHosts);

		saveAllocation();

		ExecutionTimeMeasurer.start("optimizeAllocationVmSelection");
		List<? extends Vm> vmsToMigrate = getVmsToMigrateFromHosts(overUtilizedHosts);
		getExecutionTimeHistoryVmSelection().add(ExecutionTimeMeasurer.end("optimizeAllocationVmSelection"));

		/*try
		{
			if (BwHelper.writeFile)
			{
				BwHelper.outputMigrationFile.write("Number of migrated VM: " + Integer
						.toString(vmsToMigrate.size()) + ", ");
				//BwHelper.outputMigrationFile.newLine();
			}
		}
		catch (Exception e) 
		{
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}*/
		
		Log.printLine("Reallocation of VMs from the over-utilized hosts:");
		ExecutionTimeMeasurer.start("optimizeAllocationVmReallocation");
		List<Map<String, Object>> migrationMap = null;
		if (BwHelper.BestFitVM)
		{
			migrationMap = getNewVmPlacementBestfitVm(vmsToMigrate, new HashSet<Host>(
					overUtilizedHosts));
		}
		else
		{
			migrationMap = getNewVmPlacement(vmsToMigrate, new HashSet<Host>(
					overUtilizedHosts));
		}
		
		getExecutionTimeHistoryVmReallocation().add(
				ExecutionTimeMeasurer.end("optimizeAllocationVmReallocation"));
		Log.printLine();

		migrationMap.addAll(getMigrationMapFromUnderUtilizedHosts(overUtilizedHosts));

		restoreAllocation();

		getExecutionTimeHistoryTotal().add(ExecutionTimeMeasurer.end("optimizeAllocationTotal"));

		return migrationMap;
	}

	/**
	 * Gets the migration map from under utilized hosts.
	 * 
	 * @param overUtilizedHosts the over utilized hosts
	 * @return the migration map from under utilized hosts
	 */
	protected List<Map<String, Object>> getMigrationMapFromUnderUtilizedHosts(
			List<PowerHostUtilizationHistory> overUtilizedHosts) {
		List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
		List<PowerHost> switchedOffHosts = getSwitchedOffHosts();

		// over-utilized hosts + hosts that are selected to migrate VMs to from over-utilized hosts
		Set<PowerHost> excludedHostsForFindingUnderUtilizedHost = new HashSet<PowerHost>();
		excludedHostsForFindingUnderUtilizedHost.addAll(overUtilizedHosts);
		excludedHostsForFindingUnderUtilizedHost.addAll(switchedOffHosts);
		excludedHostsForFindingUnderUtilizedHost.addAll(extractHostListFromMigrationMap(migrationMap));

		// over-utilized + under-utilized hosts
		Set<PowerHost> excludedHostsForFindingNewVmPlacement = new HashSet<PowerHost>();
		excludedHostsForFindingNewVmPlacement.addAll(overUtilizedHosts);
		excludedHostsForFindingNewVmPlacement.addAll(switchedOffHosts);

		int numberOfHosts = getHostList().size();

		while (true) {
			if (numberOfHosts == excludedHostsForFindingUnderUtilizedHost.size()) {
				break;
			}

			PowerHost underUtilizedHost = getUnderUtilizedHost(excludedHostsForFindingUnderUtilizedHost);
			if (underUtilizedHost == null) {
				break;
			}

			Log.printLine("Under-utilized host: host #" + underUtilizedHost.getId() + "\n");

			excludedHostsForFindingUnderUtilizedHost.add(underUtilizedHost);
			excludedHostsForFindingNewVmPlacement.add(underUtilizedHost);

			List<? extends Vm> vmsToMigrateFromUnderUtilizedHost = getVmsToMigrateFromUnderUtilizedHost(underUtilizedHost);
			if (vmsToMigrateFromUnderUtilizedHost.isEmpty()) {
				continue;
			}

			Log.print("Reallocation of VMs from the under-utilized host: ");
			if (!Log.isDisabled()) {
				for (Vm vm : vmsToMigrateFromUnderUtilizedHost) {
					Log.print(vm.getId() + " ");
				}
			}
			Log.printLine();

			List<Map<String, Object>> newVmPlacement = getNewVmPlacementFromUnderUtilizedHost(
					vmsToMigrateFromUnderUtilizedHost,
					excludedHostsForFindingNewVmPlacement);

			excludedHostsForFindingUnderUtilizedHost.addAll(extractHostListFromMigrationMap(newVmPlacement));

			migrationMap.addAll(newVmPlacement);
			Log.printLine();
		}

		return migrationMap;
	}

	protected List<Map<String, Object>> getMigrationMapFromUnderUtilizedHosts0(
			List<PowerHostUtilizationHistory> overUtilizedHosts) {
		List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
		List<PowerHost> switchedOffHosts = getSwitchedOffHosts();

		// over-utilized hosts + hosts that are selected to migrate VMs to from over-utilized hosts
		Set<PowerHost> excludedHostsForFindingUnderUtilizedHost = new HashSet<PowerHost>();
		excludedHostsForFindingUnderUtilizedHost.addAll(overUtilizedHosts);
		excludedHostsForFindingUnderUtilizedHost.addAll(switchedOffHosts);
		excludedHostsForFindingUnderUtilizedHost.addAll(extractHostListFromMigrationMap(migrationMap));

		// over-utilized + under-utilized hosts
		Set<PowerHost> excludedHostsForFindingNewVmPlacement = new HashSet<PowerHost>();
		excludedHostsForFindingNewVmPlacement.addAll(overUtilizedHosts);
		excludedHostsForFindingNewVmPlacement.addAll(switchedOffHosts);

		int numberOfHosts = getHostList().size();

		while (true) {
			if (numberOfHosts == excludedHostsForFindingUnderUtilizedHost.size()) {
				break;
			}

			PowerHost underUtilizedHost = getUnderUtilizedHost(excludedHostsForFindingUnderUtilizedHost);
			if (underUtilizedHost == null) {
				break;
			}

			Log.printLine("Under-utilized host: host #" + underUtilizedHost.getId() + "\n");

			excludedHostsForFindingUnderUtilizedHost.add(underUtilizedHost);
			excludedHostsForFindingNewVmPlacement.add(underUtilizedHost);

			List<? extends Vm> vmsToMigrateFromUnderUtilizedHost = getVmsToMigrateFromUnderUtilizedHost(underUtilizedHost);
			if (vmsToMigrateFromUnderUtilizedHost.isEmpty()) {
				continue;
			}

			Log.print("Reallocation of VMs from the under-utilized host: ");
			if (!Log.isDisabled()) {
				for (Vm vm : vmsToMigrateFromUnderUtilizedHost) {
					Log.print(vm.getId() + " ");
				}
			}
			Log.printLine();

			List<Map<String, Object>> newVmPlacement = getNewVmPlacementFromUnderUtilizedHost(
					vmsToMigrateFromUnderUtilizedHost,
					excludedHostsForFindingNewVmPlacement);

			excludedHostsForFindingUnderUtilizedHost.addAll(extractHostListFromMigrationMap(newVmPlacement));

			migrationMap.addAll(newVmPlacement);
			Log.printLine();
		}

		return migrationMap;
	}
	
	/**
	 * Prints the over utilized hosts.
	 * 
	 * @param overUtilizedHosts the over utilized hosts
	 */
	protected void printOverUtilizedHosts(List<PowerHostUtilizationHistory> overUtilizedHosts) {
		if (!Log.isDisabled()) {
			Log.printLine("Over-utilized hosts:");
			for (PowerHostUtilizationHistory host : overUtilizedHosts) {
				Log.printLine("Host #" + host.getId());
			}
			Log.printLine();
		}
	}

	/**
	 * Find host for vm.
	 * 
	 * @param vm the vm
	 * @param excludedHosts the excluded hosts
	 * @return the power host
	 */
	public PowerHost findHostForVm1(Vm vm, Set<? extends Host> excludedHosts) {
		double minPower = Double.MAX_VALUE;
		PowerHost allocatedHost = null;

		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (excludedHosts.contains(host)) {
				continue;
			}
			if (host.isSuitableForVm(vm)) {
				if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
					continue;
				}

				try {
					double powerAfterAllocation = getPowerAfterAllocation(host, vm);
					if (powerAfterAllocation != -1) {
						double powerDiff = powerAfterAllocation - host.getPower();
						if (powerDiff < minPower) {
							minPower = powerDiff;
							allocatedHost = host;
						}
					}
				} catch (Exception e) {
				}
			}
		}
		return allocatedHost;
	}

	public PowerHost findHostForVm(Vm vm, Set<? extends Host> excludedHosts) 
	{
		double minPower = Double.MAX_VALUE;
		PowerHost allocatedHost = null;
		double maxPredict = Double.MIN_VALUE;
		if (BwHelper.HostSort)
		{
			sortByCpuUtilizationDecrease(this.<PowerHost> getHostList());
		}
		
		if (BwHelper.BestFitHost)
		{
			for (PowerHost host : this.<PowerHost> getHostList())
			{
				if (excludedHosts.contains(host))
				{
					continue;
				}
				if (host.isSuitableForVm(vm))
				{
					if (getUtilizationOfCpuMips(host) != 0
							&& isHostOverUtilizedAfterAllocationThreshold(host,
									vm, BwHelper.THRESHOLD))
					{
						continue;
					}

					try
					{
						double prediction = getPredictAfterAllocationThreshold(host, vm, BwHelper.THRESHOLD);
						
						if (prediction < 1)
						{
							if (prediction > maxPredict)
							{
								maxPredict = prediction;
								allocatedHost = host;
							}
						}
					}
					catch (Exception e)
					{
					}
				}
			}
		}
		else
		{

			for (PowerHost host : this.<PowerHost> getHostList())
			{
				if (excludedHosts.contains(host))
				{
					continue;
				}
				if (host.isSuitableForVm(vm))
				{
					if (getUtilizationOfCpuMips(host) != 0
							&& isHostOverUtilizedAfterAllocationThreshold(host,
									vm, BwHelper.THRESHOLD))
					{
						continue;
					}

					try
					{
						double powerAfterAllocation = getPowerAfterAllocation(host, vm);
						if (powerAfterAllocation != -1)
						{
							double powerDiff = powerAfterAllocation
									- host.getPower();
							if (powerDiff < minPower)
							{
								minPower = powerDiff;
								allocatedHost = host;
							}
						}
					}
					catch (Exception e)
					{
					}
				}
			}
		}
		return allocatedHost;
	}
	
	public PowerHost findHostForVmLeastIncreased(Vm vm,
			Set<? extends Host> excludedHosts)
	{
		double minPower = Double.MAX_VALUE;
		PowerHost allocatedHost = null;
		
		if (BwHelper.HostSort)
		{
			sortByCpuUtilizationDecrease(this.<PowerHost> getHostList());
		}

		for (PowerHost host : this.<PowerHost> getHostList())
		{
			if (excludedHosts.contains(host))
			{
				continue;
			}
			if (host.isSuitableForVm(vm))
			{
				if (getUtilizationOfCpuMips(host) != 0
						&& isHostOverUtilizedAfterAllocationThreshold(host, vm,
								BwHelper.THRESHOLD))
				{
					continue;
				}

				try
				{
					double powerAfterAllocation = getPowerAfterAllocation(host,
							vm);
					if (powerAfterAllocation != -1)
					{
						double powerDiff = powerAfterAllocation
								- host.getPower();
						if (powerDiff < minPower)
						{
							minPower = powerDiff;
							allocatedHost = host;
						}
					}
				}
				catch (Exception e)
				{
				}
			}
		}

		return allocatedHost;
	}
	
	/**
	 * Sort by host cpu utilization.
	 * 
	 * @param hostList the host list
	 */
	public void sortByCpuUtilizationDecrease(List<PowerHost> hostList) {
		Collections.sort(hostList, new Comparator<PowerHost>() {

			@Override
			public int compare(PowerHost a, PowerHost b) throws ClassCastException {
				Double aUtilization = getUtilizationOfCpuMips(a);
				Double bUtilization = getUtilizationOfCpuMips(b);
				return bUtilization.compareTo(aUtilization);
			}
		});
	}
	
	/**
	 * Checks if is host over utilized after allocation.
	 * 
	 * @param host the host
	 * @param vm the vm
	 * @return true, if is host over utilized after allocation
	 */
	protected boolean isHostOverUtilizedAfterAllocation(PowerHost host, Vm vm) {
		boolean isHostOverUtilizedAfterAllocation = true;
		if (host.vmCreate(vm)) {
			isHostOverUtilizedAfterAllocation = isHostOverUtilized(host);
			host.vmDestroy(vm);
		}
		return isHostOverUtilizedAfterAllocation;
	}

	/**
	 * Checks if is host over utilized after allocation.
	 * 
	 * @param host the host
	 * @param vm the vm
	 * @return true, if is host over utilized after allocation
	 */
	protected boolean isHostOverUtilizedAfterAllocationThreshold(PowerHost host, Vm vm, double threshold) {
		boolean isHostOverUtilizedAfterAllocation = true;
		if (host.vmCreate(vm)) {
			isHostOverUtilizedAfterAllocation = isHostOverUtilizedThreshold(host, threshold);
			//isHostOverUtilizedAfterAllocation = isHostOverUtilized(host);
			host.vmDestroy(vm);
		}
		return isHostOverUtilizedAfterAllocation;
	}
	
	protected double getPredictAfterAllocationThreshold(PowerHost host, Vm vm, double threshold)
	{
		double predict = 1.0;
		if (host.vmCreate(vm)) {
			predict = getPredictThreshold(host, threshold);
			//isHostOverUtilizedAfterAllocation = isHostOverUtilized(host);
			host.vmDestroy(vm);
		}
		return predict;
	}
	
	/**
	 * Find host for vm.
	 * 
	 * @param vm the vm
	 * @return the power host
	 */
	@Override
	public PowerHost findHostForVm(Vm vm) {
		Set<Host> excludedHosts = new HashSet<Host>();
		if (vm.getHost() != null) {
			excludedHosts.add(vm.getHost());
		}
		//return findHostForVm(vm, excludedHosts);
		return findHostForVmLeastIncreased(vm, excludedHosts);
	}

	/**
	 * Extract host list from migration map.
	 * 
	 * @param migrationMap the migration map
	 * @return the list
	 */
	protected List<PowerHost> extractHostListFromMigrationMap(List<Map<String, Object>> migrationMap) {
		List<PowerHost> hosts = new LinkedList<PowerHost>();
		for (Map<String, Object> map : migrationMap) {
			hosts.add((PowerHost) map.get("host"));
		}
		return hosts;
	}

	/**
	 * Gets the new vm placement.
	 * 
	 * @param vmsToMigrate the vms to migrate
	 * @param excludedHosts the excluded hosts
	 * @return the new vm placement
	 */
	protected List<Map<String, Object>> getNewVmPlacement1(
			List<? extends Vm> vmsToMigrate, Set<? extends Host> excludedHosts)
	{
		List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
		if (vmsToMigrate.size() > BwHelper.overPSOThreshold)
		{
			migrationMap = getVmMapByPSO(vmsToMigrate, excludedHosts);
		}
		else
		{
			PowerVmList.sortByCpuUtilization(vmsToMigrate);
			for (Vm vm : vmsToMigrate)
			{
				PowerHost allocatedHost = findHostForVm(vm, excludedHosts);
				if (allocatedHost != null)
				{
					allocatedHost.vmCreate(vm);
					Log.printLine("VM #" + vm.getId() + " allocated to host #"
							+ allocatedHost.getId());
					Map<String, Object> migrate = new HashMap<String, Object>();
					migrate.put("vm", vm);
					migrate.put("host", allocatedHost);
					migrationMap.add(migrate);
				}
			}
		}
		return migrationMap;
	}

	protected List<Map<String, Object>> getNewVmPlacement(
			List<? extends Vm> vmsToMigrate, Set<? extends Host> excludedHosts)
	{
		List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();

		if (BwHelper.VMIncrease)
		{
			PowerVmList.sortByCpuUtilizationIncrease(vmsToMigrate);
		
		}
		else
		{
			PowerVmList.sortByCpuUtilization(vmsToMigrate);
		}
		
		for (Vm vm : vmsToMigrate)
		{
			PowerHost allocatedHost = findHostForVm(vm, excludedHosts);
			if (allocatedHost != null)
			{
				allocatedHost.vmCreate(vm);
				Log.printLine("VM #" + vm.getId() + " allocated to host #"
						+ allocatedHost.getId());
				Map<String, Object> migrate = new HashMap<String, Object>();
				migrate.put("vm", vm);
				migrate.put("host", allocatedHost);
				migrationMap.add(migrate);
			}
		}
		
		return migrationMap;
	}
	
	
	protected List<Map<String, Object>> getNewVmPlacementBestfitVm(
			List<? extends Vm> vmsToMigrate, Set<? extends Host> excludedHosts)
	{
		List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();

		PowerVmList.sortByCpuUtilizationIncrease(vmsToMigrate);
				
		List<PowerHost> potentialHost = getUsefulHostList(vmsToMigrate, excludedHosts);
		
		sortByCpuUtilizationDecrease(potentialHost);
		
		List<Vm> migratedVm = new LinkedList<Vm>();
		for (Vm vm : vmsToMigrate)
		{
			migratedVm.add(vm);
		}
		
		for (PowerHost host : potentialHost)
		{
			int numOfMigratedVms = migratedVm.size();
			if (numOfMigratedVms == 0)
			{
				break;
			}
			int availableUtilization = 100 - (int)(getUtilizationOfCpuMips(host) / host.getTotalMips() * 100);
			
			int B[][] = new int[numOfMigratedVms + 1][availableUtilization + 1];
			Vm C[][][] = new Vm[numOfMigratedVms + 1][availableUtilization + 1][numOfMigratedVms];
			int lengthC[][] = new int[numOfMigratedVms + 1][availableUtilization + 1];
			for (int m = 0; m < numOfMigratedVms + 1; m++)
			{
				for (int n = 0; n < availableUtilization + 1; n++)
				{
					lengthC[m][n] = 0;
				}
			}
						
			for (int w = 0; w < availableUtilization + 1; w++)
			{
				B[0][w] = 0;
			}
			for (int i = 1; i < numOfMigratedVms + 1; i++)
			{
				for (int w = 0; w < availableUtilization + 1; w++)
				{
					int vmUtilization = (int) (migratedVm.get(i - 1).getCurrentRequestedTotalMips() / host.getTotalMips() * 100);
					if (vmUtilization < w)
					{
						int prediction = getPredictAfterVmsAllocationThreshold(host, migratedVm.get(i - 1), C[i - 1][w- vmUtilization], lengthC[i - 1][w- vmUtilization], BwHelper.THRESHOLD);
						
						if (prediction > B[i - 1][w - vmUtilization] && prediction < 100)
						{
							B[i][w] = prediction;
							C[i][w][lengthC[i][w]] = migratedVm.get(i - 1);
							lengthC[i][w]++;
						}
						else
						{
							B[i][w] = B[i - 1][w];
						}
					}
					else
					{
						B[i][w] = B[i - 1][w];
					}
				}
			}
						
			for (int m = 0; m < lengthC[numOfMigratedVms][availableUtilization]; m++)
			{
				host.vmCreate(C[numOfMigratedVms][availableUtilization][m]);
				Log.printLine("VM #" + C[numOfMigratedVms][availableUtilization][m].getId() + " allocated to host #"
						+ host.getId());
				Map<String, Object> migrate = new HashMap<String, Object>();
				migrate.put("vm", C[numOfMigratedVms][availableUtilization][m]);
				migrate.put("host", host);
				migrationMap.add(migrate);
				migratedVm.remove(C[numOfMigratedVms][availableUtilization][m]);
			}
		}
		
		return migrationMap;
	}
	
	private int getPredictAfterVmsAllocationThreshold(PowerHost host, Vm newVm, Vm[] C, int lengthOfC, double threshold)
	{
		int predict = 0;
		for (int i = 0; i < lengthOfC; i++)
		{
			if (!host.vmCreate(C[i])) 
			{
				break;
			}
		}
		if (host.vmCreate(newVm)) 
		{
			predict = (int)(getPredictThreshold(host, threshold) * 100);
			host.vmDestroy(newVm);
		}
		for (int i = 0; i < lengthOfC; i++)
		{
			host.vmDestroy(C[i]);
		}
		return predict;		
	}
	
	protected List<Map<String, Object>> getVmMapByPSO(
            List<? extends Vm> vmsToMigrate,
            Set<? extends Host> excludedHosts){
        List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
        List<PowerHost> potentialHost = getUsefulHostList(vmsToMigrate, excludedHosts);
        int dimension = vmsToMigrate.size();
        BwHelper.dimension = dimension;
        EnergyCalculator energyCalculator = new EnergyCalculator(potentialHost, vmsToMigrate);
        EnergyFitnessFunction fitnessFunction = new EnergyFitnessFunction(energyCalculator);
        EnergyParticle particle = new EnergyParticle();
        System.out.println("Begin: Example 1\n");

        // Create a swarm (using 'MyParticle' as sample particle and 'MyFitnessFunction' as fitness function)
        Swarm swarm = new Swarm(Swarm.DEFAULT_NUMBER_OF_PARTICLES * 4, particle, fitnessFunction);

        // Use neighborhood
        Neighborhood neigh = new Neighborhood1D(Swarm.DEFAULT_NUMBER_OF_PARTICLES * 4 / 5, true);
        swarm.setNeighborhood(neigh);
        swarm.setNeighborhoodIncrement(0.9);
        swarm.setParticleIncrement(0.8);
        swarm.setGlobalIncrement(0.8);
        // Set position (and velocity) constraints. I.e.: where to look for solutions
        swarm.setInertia(0.95);
        swarm.setMaxPosition(potentialHost.size()-1);
        swarm.setMinPosition(0);
        swarm.setMaxMinVelocity(0.1);
        int numberOfIterations = 100;
        boolean showGraphics = false;

        if (showGraphics) {
            int displayEvery = numberOfIterations / 100 + 1;
            SwarmShow2D ss2d = new SwarmShow2D(swarm, numberOfIterations, displayEvery, true);
            ss2d.run();
        } else {
            // Optimize (and time it)
            for (int i = 0; i < numberOfIterations; i++){
                swarm.evolve();
                System.out.println(swarm.getBestFitness());
            }
        }

        // Print results
        System.out.println(swarm.toStringStats());
        System.out.println("End: Example 1");
        double[] bestPosition = swarm.getBestPosition();
        for(int i=0; i<bestPosition.length; i++){
            Map<String, Object> migrate = new HashMap<String, Object>();
            Vm vm = vmsToMigrate.get(i);
            migrate.put("vm", vm);
            PowerHost host = potentialHost.get((int) bestPosition[i]);
            migrate.put("host", host);
            host.vmCreate(vm);
            migrationMap.add(migrate);
        }
        return migrationMap;
    }
	
	protected List<PowerHost> getUsefulHostList(
			List<? extends Vm> vmsToMigrate, Set<? extends Host> excludedHosts)
	{
		List<PowerHost> result = new LinkedList<PowerHost>();
		for (PowerHost host : this.<PowerHost> getHostList())
		{
			if (excludedHosts.contains(host))
			{
				continue;
			}
			boolean suitable = true;
			for (Vm vm : vmsToMigrate)
			{
				if (!host.isSuitableForVm(vm))
				{
					suitable = false;
					break;
				}
				if (getUtilizationOfCpuMips(host) != 0
						&& isHostOverUtilizedAfterAllocation(host, vm))
				{
					suitable = false;
					break;
				}
			}
			if (suitable)
			{
				result.add(host);
			}
		}
		return result;
	}
	
	/**
	 * Gets the new vm placement from under utilized host.
	 * 
	 * @param vmsToMigrate the vms to migrate
	 * @param excludedHosts the excluded hosts
	 * @return the new vm placement from under utilized host
	 */
	protected List<Map<String, Object>> getNewVmPlacementFromUnderUtilizedHost(
			List<? extends Vm> vmsToMigrate,
			Set<? extends Host> excludedHosts) {
		List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
		
		if (BwHelper.VMIncrease)
		{
			PowerVmList.sortByCpuUtilizationIncrease(vmsToMigrate);
		
		}
		else
		{
			PowerVmList.sortByCpuUtilization(vmsToMigrate);
		}
		
		for (Vm vm : vmsToMigrate) {
			
			/*PowerHost oldHost = (PowerHost) vm.getHost();
			oldHost.vmDestroy(vm);*/
			
			PowerHost allocatedHost = findHostForVm(vm, excludedHosts);
			if (allocatedHost != null) {
				allocatedHost.vmCreate(vm);
				Log.printLine("VM #" + vm.getId() + " allocated to host #" + allocatedHost.getId());

				Map<String, Object> migrate = new HashMap<String, Object>();
				migrate.put("vm", vm);
				migrate.put("host", allocatedHost);
				migrationMap.add(migrate);
			} else {
				Log.printLine("Not all VMs can be reallocated from the host, reallocation cancelled");
				for (Map<String, Object> map : migrationMap) {
					((Host) map.get("host")).vmDestroy((Vm) map.get("vm"));
				}
				migrationMap.clear();
				break;
			}
		}
		return migrationMap;
	}
	
	protected List<Map<String, Object>> getNewVmPlacementFromUnderUtilizedHost1(
			List<? extends Vm> vmsToMigrate,
			Set<? extends Host> excludedHosts) {
		List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
		PowerVmList.sortByCpuUtilization(vmsToMigrate);
		for (Vm vm : vmsToMigrate) {
			
			/*PowerHost oldHost = (PowerHost) vm.getHost();
			oldHost.vmDestroy(vm);*/
			
			PowerHost allocatedHost = findHostForVm(vm, excludedHosts);
			if (allocatedHost != null) {
				allocatedHost.vmCreate(vm);
				Log.printLine("VM #" + vm.getId() + " allocated to host #" + allocatedHost.getId());

				Map<String, Object> migrate = new HashMap<String, Object>();
				migrate.put("vm", vm);
				migrate.put("host", allocatedHost);
				migrationMap.add(migrate);
			} else {
				Log.printLine("Not all VMs can be reallocated from the host, reallocation cancelled");
				for (Map<String, Object> map : migrationMap) {
					((Host) map.get("host")).vmDestroy((Vm) map.get("vm"));
				}
				migrationMap.clear();
				break;
			}
		}
		return migrationMap;
	}
	/**
	 * Gets the vms to migrate from hosts.
	 * 
	 * @param overUtilizedHosts the over utilized hosts
	 * @return the vms to migrate from hosts
	 */
	protected
			List<? extends Vm>
			getVmsToMigrateFromHosts0(List<PowerHostUtilizationHistory> overUtilizedHosts) {
		List<Vm> vmsToMigrate = new LinkedList<Vm>();
		for (PowerHostUtilizationHistory host : overUtilizedHosts) {
			while (true) {
				Vm vm = getVmSelectionPolicy().getVmToMigrate(host);
				if (vm == null) {
					break;
				}
				vmsToMigrate.add(vm);
				host.vmDestroy(vm);
				if (!isHostOverUtilized(host)) {
					break;
				}
			}
		}
		return vmsToMigrate;
	}
	
	protected List<? extends Vm> getVmsToMigrateFromHosts(
			List<PowerHostUtilizationHistory> overUtilizedHosts)
	{
		List<Vm> vmsToMigrate = new LinkedList<Vm>();
		List<Host> hostToSave = new LinkedList<Host>();
		for (PowerHostUtilizationHistory host : overUtilizedHosts)
		{
			while (true)
			{
				Vm vm = getVmSelectionPolicy().getVmToMigrate(host);
				if (vm == null)
				{
					break;
				}
				vmsToMigrate.add(vm);
				hostToSave.add(host);
				host.vmDestroy(vm);
				if (!isHostOverUtilized(host))
				{
					break;
				}
			}
		}
		BwHelper.oldHostList = hostToSave;
		return vmsToMigrate;
	}

	/**
	 * Gets the vms to migrate from under utilized host.
	 * 
	 * @param host the host
	 * @return the vms to migrate from under utilized host
	 */
	protected List<? extends Vm> getVmsToMigrateFromUnderUtilizedHost(PowerHost host) {
		List<Vm> vmsToMigrate = new LinkedList<Vm>();
		for (Vm vm : host.getVmList()) {
			if (!vm.isInMigration()) {
				vmsToMigrate.add(vm);
			}
		}
		return vmsToMigrate;
	}

	/**
	 * Gets the over utilized hosts.
	 * 
	 * @return the over utilized hosts
	 */
	protected List<PowerHostUtilizationHistory> getOverUtilizedHosts() {
		List<PowerHostUtilizationHistory> overUtilizedHosts = new LinkedList<PowerHostUtilizationHistory>();
		for (PowerHostUtilizationHistory host : this.<PowerHostUtilizationHistory> getHostList()) {
			if (isHostOverUtilized(host)) {
				overUtilizedHosts.add(host);
			}
		}
		return overUtilizedHosts;
	}

	/**
	 * Gets the switched off host.
	 * 
	 * @return the switched off host
	 */
	protected List<PowerHost> getSwitchedOffHosts() {
		List<PowerHost> switchedOffHosts = new LinkedList<PowerHost>();
		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (host.getUtilizationOfCpu() == 0) {
				switchedOffHosts.add(host);
			}
		}
		return switchedOffHosts;
	}

	/**
	 * Gets the under utilized host.
	 * 
	 * @param excludedHosts the excluded hosts
	 * @return the under utilized host
	 */
	protected PowerHost getUnderUtilizedHost(Set<? extends Host> excludedHosts) {
		double minUtilization = 1;
		PowerHost underUtilizedHost = null;
		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (excludedHosts.contains(host)) {
				continue;
			}
			double utilization = host.getUtilizationOfCpu();
			if (utilization > 0 && utilization < minUtilization
					&& !areAllVmsMigratingOutOrAnyVmMigratingIn(host)) {
				minUtilization = utilization;
				underUtilizedHost = host;
			}
		}
		return underUtilizedHost;
	}

	/**
	 * Checks whether all vms are in migration.
	 * 
	 * @param host the host
	 * @return true, if successful
	 */
	protected boolean areAllVmsMigratingOutOrAnyVmMigratingIn(PowerHost host) {
		for (PowerVm vm : host.<PowerVm> getVmList()) {
			if (!vm.isInMigration()) {
				return false;
			}
			if (host.getVmsMigratingIn().contains(vm)) {
				return true;
			}
		}
		return true;
	}

	/**
	 * Checks if is host over utilized.
	 * 
	 * @param host the host
	 * @return true, if is host over utilized
	 */
	protected abstract boolean isHostOverUtilized(PowerHost host);

	/**
	 * Checks if is host over utilized.
	 * 
	 * @param host the host
	 * @return true, if is host over utilized
	 */
	protected abstract boolean isHostOverUtilizedThreshold(PowerHost host, double threshold);
	
	protected double getPredictThreshold(PowerHost host, double threshold)
	{
		return 1.0;
	}
	/**
	 * Adds the history value.
	 * 
	 * @param host the host
	 * @param metric the metric
	 */
	protected void addHistoryEntry(HostDynamicWorkload host, double metric) {
		int hostId = host.getId();
		if (!getTimeHistory().containsKey(hostId)) {
			getTimeHistory().put(hostId, new LinkedList<Double>());
		}
		if (!getUtilizationHistory().containsKey(hostId)) {
			getUtilizationHistory().put(hostId, new LinkedList<Double>());
		}
		if (!getMetricHistory().containsKey(hostId)) {
			getMetricHistory().put(hostId, new LinkedList<Double>());
		}
		if (!getTimeHistory().get(hostId).contains(CloudSim.clock())) {
			getTimeHistory().get(hostId).add(CloudSim.clock());
			getUtilizationHistory().get(hostId).add(host.getUtilizationOfCpu());
			getMetricHistory().get(hostId).add(metric);
		}
	}

	/**
	 * Save allocation.
	 */
	protected void saveAllocation() {
		getSavedAllocation().clear();
		for (Host host : getHostList()) {
			for (Vm vm : host.getVmList()) {
				if (host.getVmsMigratingIn().contains(vm)) {
					continue;
				}
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("host", host);
				map.put("vm", vm);
				getSavedAllocation().add(map);
			}
		}
	}

	/**
	 * Restore allocation.
	 */
	protected void restoreAllocation() {
		for (Host host : getHostList()) {
			host.vmDestroyAll();
			host.reallocateMigratingInVms();
		}
		for (Map<String, Object> map : getSavedAllocation()) {
			Vm vm = (Vm) map.get("vm");
			PowerHost host = (PowerHost) map.get("host");
			if (!host.vmCreate(vm)) {
				Log.printLine("Couldn't restore VM #" + vm.getId() + " on host #" + host.getId());
				System.exit(0);
			}
			getVmTable().put(vm.getUid(), host);
		}
	}

	/**
	 * Gets the power after allocation.
	 * 
	 * @param host the host
	 * @param vm the vm
	 * 
	 * @return the power after allocation
	 */
	protected double getPowerAfterAllocation(PowerHost host, Vm vm) {
		double power = 0;
		try {
			power = host.getPowerModel().getPower(getMaxUtilizationAfterAllocation(host, vm));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
		return power;
	}

	/**
	 * Gets the power after allocation. We assume that load is balanced between PEs. The only
	 * restriction is: VM's max MIPS < PE's MIPS
	 * 
	 * @param host the host
	 * @param vm the vm
	 * 
	 * @return the power after allocation
	 */
	protected double getMaxUtilizationAfterAllocation(PowerHost host, Vm vm) {
		double requestedTotalMips = vm.getCurrentRequestedTotalMips();
		double hostUtilizationMips = getUtilizationOfCpuMips(host);
		double hostPotentialUtilizationMips = hostUtilizationMips + requestedTotalMips;
		double pePotentialUtilization = hostPotentialUtilizationMips / host.getTotalMips();
		return pePotentialUtilization;
	}
	
	/**
	 * Gets the utilization of the CPU in MIPS for the current potentially allocated VMs.
	 *
	 * @param host the host
	 *
	 * @return the utilization of the CPU in MIPS
	 */
	protected double getUtilizationOfCpuMips(PowerHost host) {
		double hostUtilizationMips = 0;
		for (Vm vm2 : host.getVmList()) {
			if (host.getVmsMigratingIn().contains(vm2)) {
				// calculate additional potential CPU usage of a migrating in VM
				hostUtilizationMips += host.getTotalAllocatedMipsForVm(vm2) * 0.9 / 0.1;
			}
			hostUtilizationMips += host.getTotalAllocatedMipsForVm(vm2);
		}
		return hostUtilizationMips;
	}

	/**
	 * Gets the saved allocation.
	 * 
	 * @return the saved allocation
	 */
	protected List<Map<String, Object>> getSavedAllocation() {
		return savedAllocation;
	}

	/**
	 * Sets the vm selection policy.
	 * 
	 * @param vmSelectionPolicy the new vm selection policy
	 */
	protected void setVmSelectionPolicy(PowerVmSelectionPolicy vmSelectionPolicy) {
		this.vmSelectionPolicy = vmSelectionPolicy;
	}

	/**
	 * Gets the vm selection policy.
	 * 
	 * @return the vm selection policy
	 */
	protected PowerVmSelectionPolicy getVmSelectionPolicy() {
		return vmSelectionPolicy;
	}

	/**
	 * Gets the utilization history.
	 * 
	 * @return the utilization history
	 */
	public Map<Integer, List<Double>> getUtilizationHistory() {
		return utilizationHistory;
	}

	/**
	 * Gets the metric history.
	 * 
	 * @return the metric history
	 */
	public Map<Integer, List<Double>> getMetricHistory() {
		return metricHistory;
	}

	/**
	 * Gets the time history.
	 * 
	 * @return the time history
	 */
	public Map<Integer, List<Double>> getTimeHistory() {
		return timeHistory;
	}

	/**
	 * Gets the execution time history vm selection.
	 * 
	 * @return the execution time history vm selection
	 */
	public List<Double> getExecutionTimeHistoryVmSelection() {
		return executionTimeHistoryVmSelection;
	}

	/**
	 * Gets the execution time history host selection.
	 * 
	 * @return the execution time history host selection
	 */
	public List<Double> getExecutionTimeHistoryHostSelection() {
		return executionTimeHistoryHostSelection;
	}

	/**
	 * Gets the execution time history vm reallocation.
	 * 
	 * @return the execution time history vm reallocation
	 */
	public List<Double> getExecutionTimeHistoryVmReallocation() {
		return executionTimeHistoryVmReallocation;
	}

	/**
	 * Gets the execution time history total.
	 * 
	 * @return the execution time history total
	 */
	public List<Double> getExecutionTimeHistoryTotal() {
		return executionTimeHistoryTotal;
	}

}
