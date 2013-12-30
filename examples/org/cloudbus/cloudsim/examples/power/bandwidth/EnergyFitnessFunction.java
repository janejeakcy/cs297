package org.cloudbus.cloudsim.examples.power.bandwidth;

import net.sourceforge.jswarm_pso.FitnessFunction;

/**
 * Created with IntelliJ IDEA.
 * User: Kai
 * Date: 11/5/13
 * Time: 14:46
 * To change this template use File | Settings | File Templates.
 */
public class EnergyFitnessFunction extends FitnessFunction {
	private EnergyCalculator energyCalculator;
	
	public EnergyFitnessFunction(EnergyCalculator energyCalculator){
        super(false);
		this.energyCalculator = energyCalculator;
	}
	
	public double evaluate(double position[]) {
		double result = 0;
        for (int i=0; i<position.length; i++){
            int vmIndex = i;
            int hostIndex = (int) position[i];
            result += energyCalculator.getTotalEnergy(vmIndex, hostIndex);
        }
		return result;
	}


}

