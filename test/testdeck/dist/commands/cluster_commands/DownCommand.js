"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const ClusterManager_1 = require("../../ClusterManager");
const Config_1 = require("../../Config");
class ProvisionCommand {
    constructor() {
        this.command = "down";
        this.describe = "Bring Down a cluster";
    }
    builder(yargs) {
        return yargs
            .option('config', {
            describe: 'Cluster configuration location',
            type: 'string'
        });
    }
    async handler(opts) {
        const config = await Config_1.Config.Load('./config.yml', './config.user.yml');
        const clusterConfig = {
            image: opts.image || config.baseImage,
            licenseFile: config.licenseFile
        };
        const cluster = await ClusterManager_1.ClusterFactory.CreateCluster(opts.config || config.clusterConfig, clusterConfig);
        await cluster.stopCluster();
    }
}
module.exports = new ProvisionCommand();
//# sourceMappingURL=DownCommand.js.map