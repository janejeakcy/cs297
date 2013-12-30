package org.cloudbus.cloudsim.examples.power.bandwidth;

import org.cloudbus.cloudsim.power.models.PowerModel;

/**
 * Created with IntelliJ IDEA.
 * User: Kai
 * Date: 11/24/13
 * Time: 20:47
 * To change this template use File | Settings | File Templates.
 */
public class PowerModelNoLinear implements PowerModel {
    private double p;
    private double c;
    private double alpha;

    public PowerModelNoLinear(double p, double c, double alpha){
        this.p = p;
        this.c = c;
        this.alpha = alpha;
    }

    public double getPower(double utilization) throws IllegalArgumentException {
        if (utilization < 0 || utilization > 1) {
            throw new IllegalArgumentException("Utilization value must be between 0 and 1");
        }
        double power = p + c * Math.pow(utilization*100, alpha);
        return power;
    }

}