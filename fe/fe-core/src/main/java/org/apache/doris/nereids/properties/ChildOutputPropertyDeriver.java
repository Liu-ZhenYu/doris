// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.nereids.properties;

import org.apache.doris.nereids.PlanContext;
import org.apache.doris.nereids.memo.GroupExpression;
import org.apache.doris.nereids.trees.plans.Plan;
import org.apache.doris.nereids.trees.plans.physical.PhysicalHashJoin;
import org.apache.doris.nereids.trees.plans.physical.PhysicalNestedLoopJoin;
import org.apache.doris.nereids.trees.plans.physical.PhysicalOlapScan;
import org.apache.doris.nereids.trees.plans.visitor.PlanVisitor;

import com.google.common.base.Preconditions;

import java.util.List;

/**
 * Used for property drive.
 */
public class ChildOutputPropertyDeriver extends PlanVisitor<PhysicalProperties, PlanContext> {
    /*
     *   parentPlanNode
     *         ▲
     *         │
     * childOutputProperty
     */
    private PhysicalProperties requestProperty;
    private List<PhysicalProperties> childrenOutputProperties;
    private double curTotalCost;


    public ChildOutputPropertyDeriver(PhysicalProperties requestProperty,
            List<PhysicalProperties> childrenOutputProperties, double curTotalCost) {
        this.childrenOutputProperties = childrenOutputProperties;
        this.requestProperty = requestProperty;
        this.curTotalCost = curTotalCost;
    }

    public PhysicalProperties getOutputProperties(GroupExpression groupExpression) {
        return groupExpression.getPlan().accept(this, new PlanContext(groupExpression));
    }

    public double getCurTotalCost() {
        return curTotalCost;
    }

    @Override
    public PhysicalProperties visit(Plan plan, PlanContext context) {
        return PhysicalProperties.ANY;
    }

    @Override
    public PhysicalProperties visitPhysicalHashJoin(PhysicalHashJoin<Plan, Plan> hashJoin, PlanContext context) {
        Preconditions.checkState(childrenOutputProperties.size() == 2);
        PhysicalProperties leftOutputProperty = childrenOutputProperties.get(0);
        PhysicalProperties rightOutputProperty = childrenOutputProperties.get(1);

        // broadcast
        // TODO: handle condition of broadcast
        if (rightOutputProperty.getDistributionSpec() instanceof DistributionSpecReplicated) {
            return leftOutputProperty;
        }

        // shuffle
        // TODO: handle condition of shuffle
        DistributionSpec leftDistribution = leftOutputProperty.getDistributionSpec();
        DistributionSpec rightDistribution = rightOutputProperty.getDistributionSpec();
        if (!(leftDistribution instanceof DistributionSpecHash)
                || !(rightDistribution instanceof DistributionSpecHash)) {
            Preconditions.checkState(false, "error");
            return PhysicalProperties.ANY;
        }

        return leftOutputProperty;
    }

    @Override
    public PhysicalProperties visitPhysicalNestedLoopJoin(PhysicalNestedLoopJoin<Plan, Plan> nestedLoopJoin,
            PlanContext context) {
        // TODO: copy from hash join, should update according to nested loop join properties.
        Preconditions.checkState(childrenOutputProperties.size() == 2);
        PhysicalProperties leftOutputProperty = childrenOutputProperties.get(0);
        PhysicalProperties rightOutputProperty = childrenOutputProperties.get(1);

        // broadcast
        if (rightOutputProperty.getDistributionSpec() instanceof DistributionSpecReplicated) {
            // TODO
            return leftOutputProperty;
        }

        // shuffle
        // List<SlotReference> leftSlotRefs = hashJoin.left().getOutput().stream().map(slot -> (SlotReference) slot)
        //        .collect(Collectors.toList());
        // List<SlotReference> rightSlotRefs = hashJoin.right().getOutput().stream().map(slot -> (SlotReference) slot)
        //                .collect(Collectors.toList());

        //        List<SlotReference> leftOnSlotRefs;
        //        List<SlotReference> rightOnSlotRefs;
        //        Preconditions.checkState(leftOnSlotRefs.size() == rightOnSlotRefs.size());
        DistributionSpec leftDistribution = leftOutputProperty.getDistributionSpec();
        DistributionSpec rightDistribution = rightOutputProperty.getDistributionSpec();
        if (!(leftDistribution instanceof DistributionSpecHash)
                || !(rightDistribution instanceof DistributionSpecHash)) {
            Preconditions.checkState(false, "error");
            return PhysicalProperties.ANY;
        }

        return leftOutputProperty;
    }

    @Override
    public PhysicalProperties visitPhysicalOlapScan(PhysicalOlapScan olapScan, PlanContext context) {
        if (olapScan.getDistributionSpec() instanceof DistributionSpecHash) {
            return PhysicalProperties.createHash((DistributionSpecHash) olapScan.getDistributionSpec());
        } else {
            return PhysicalProperties.ANY;
        }
    }
}
