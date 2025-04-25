### Default Config
```yaml
  Water:
    VineManipulation:
      Enabled: true
      SelectRange: 18.0 # the max distance you can select plant blocks
      PullStrength: 0.65 # the base pull strength. This is affected by Tension below!
      Cooldown:
        Self: 1000 # the cooldown for self usage
        Entity: 5500 # the cooldown when you target another entity
      Duration:
        Pull: 6000 # the max duration in MS before the vine removes itself
        Hang: 25000 # how long you can hang from the bottom face of a plant block
      Vine:
        ExtensionSpeed: 32.0 # bugged atm, dont change this, too low and youll never enter the tensioning state lol
        Segment: 
          Material: CAVE_VINES  # OAK_LEAVES, and KELP_PLANT also look cool
          DynamicMaterials: false
          MaxAngleConstraint: 135
        Length: 26.0 # the actual max length of a vine
        Performance:
          MaxSegments: 104 # the max number of segments a vine can have
          MinSegments: 3 # the min number of segments a vine can have
          DesiredSpacing: 0.25 # dont change this rn lol there's a bugged dynamic segment count issue I havent fixed yet that this affects
          FabrikIterations: 15 # can be lowered to 10. 10-15 is good, any higher is not necessary
          FabrikTolerance: 0.01 # dont change this rn lol
        GravitySag: 0.75 # how strongly gravity affects the vine rope visually
        DisplayScale: 0.15 # dont change this rn lol
        Stiffness: 0.1 # dont change this rn lol
      Tension: # tension is controlled by moving while holding a vine
        MaxForce: 3.0 # the max pull boost you can get by max tension
        ArcConstraint: 70.0 # the max arc you can "swing" by looking away from the source block
        AllowedSlack: 1.5 # dont change this rn lol
        MaxTensionDistance: 26.0 # the max distance before tension is 100%, in most cases should just match Length above
```
