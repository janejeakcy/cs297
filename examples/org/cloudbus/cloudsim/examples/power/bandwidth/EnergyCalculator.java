package org.cloudbus.cloudsim.examples.power.bandwidth;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.PowerVmAllocationPolicyMigrationAbstract;
import org.cloudbus.cloudsim.power.PowerVmSelectionPolicy;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Kai
 * Date: 11/3/13
 * Time: 15:48
 * To change this template use File | Settings | File Templates.
 */
public class EnergyCalculator {
    private List<PowerHost> hostList;
    private List<? extends Vm> vmList;

    public EnergyCalculator(List<PowerHost> hostList, List<? extends Vm> vmList){
        this.hostList = hostList;
        this.vmList = vmList;
    }

    public double getTotalEnergy(int vmIndex, int hostIndex){
        return getTransmissionEnergy(vmIndex, hostIndex) + getProcessEnergy(vmIndex, hostIndex);
    }

    public double getTransmissionEnergy(int vmIndex, int hostIndex){
        double result = 0;
        Vm vm = vmList.get(vmIndex);
        PowerHost oldHost = (PowerHost) BwHelper.oldHostList.get(vmIndex);
        PowerHost newHost = hostList.get(hostIndex);
        result = BwHelper.getTransmissionPower(oldHost,newHost,vm);
        return result;
    }

    public double getProcessEnergy(int vmIndex, int hostIndex){
        double result =0;
        Vm vm = vmList.get(vmIndex);
        PowerHost host = hostList.get(hostIndex);
        double powerAfterAllocation = getPowerAfterAllocation(host, vm);
        if (powerAfterAllocation != -1) {
            //result = powerAfterAllocation - host.getPower();
            result = powerAfterAllocation;
        }
        return result;
    }

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

    protected double getMaxUtilizationAfterAllocation(PowerHost host, Vm vm) {
        double requestedTotalMips = vm.getCurrentRequestedTotalMips();
        double hostUtilizationMips = getUtilizationOfCpuMips(host);
        double hostPotentialUtilizationMips = hostUtilizationMips + requestedTotalMips;
        double pePotentialUtilization = hostPotentialUtilizationMips / host.getTotalMips();
        return pePotentialUtilization;
    }

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

    protected boolean isHostOverUtilized(PowerHost host){
        return false;
    }

    protected boolean isHostOverUtilizedAfterAllocation(PowerHost host, Vm vm) {
        boolean isHostOverUtilizedAfterAllocation = true;
        if (host.vmCreate(vm)) {
            isHostOverUtilizedAfterAllocation = isHostOverUtilized(host);
            host.vmDestroy(vm);
        }
        return isHostOverUtilizedAfterAllocation;
    }



    public boolean ifVmCanMoveToTheHost(int vmIndex, int hostIndex){
        Vm vm = vmList.get(vmIndex);
        PowerHost host = hostList.get(hostIndex);
        if (host.isSuitableForVm(vm) && getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)){
            return true;
        }else{
            return false;
        }
    }
}
