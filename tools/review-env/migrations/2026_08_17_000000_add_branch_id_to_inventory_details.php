<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Lets a fresh database reach the stock-lookup index migration.
 *
 * `inventory_details` is created by 2018_10_20_160752_create_inventory_details_table
 * as a stub -- id and timestamps only. Its real columns arrived on deployed
 * databases by hand or through the legacy import, so a clean install is missing
 * `branch_id`, and 2026_08_18_120000_add_stock_lookup_indexes then dies with
 * "1072 Key column 'branch_id' doesn't exist in table".
 *
 * The definition is taken from the project, not invented:
 *
 *   - database/sql/*.sql is the schema of record for these tables, and every
 *     table carrying this column declares it as
 *     `branch_id` INT(10) UNSIGNED NOT NULL COMMENT 'com_branches.id';
 *   - com_branches.id is unsigned (2018_05_27_162104_create_branches_table);
 *   - the application writes it from the signed-in user's branch --
 *     CommissionSalesMaster, InventoryMasterIn and InventoryMasterOut all set
 *     'branch_id' => Auth::user()->branch_id before inserting the detail rows;
 *   - the demo seeders read it back as inventory_details.branch_id and filter on
 *     it. So it is the owning branch, and it is not nullable.
 *
 * Guarded on both sides, so it is a no-op on every database that already has the
 * column and safe to re-run. It is dated before the index migration on purpose:
 * the point is to repair the fresh-install sequence, not to silence the failure
 * after the fact.
 */
return new class extends Migration
{
    public function up(): void
    {
        if (!Schema::hasTable('inventory_details') || Schema::hasColumn('inventory_details', 'branch_id')) {
            return;
        }

        Schema::table('inventory_details', function (Blueprint $table) {
            $table->unsignedInteger('branch_id')->comment('com_branches.id');
        });
    }

    public function down(): void
    {
        if (!Schema::hasTable('inventory_details') || !Schema::hasColumn('inventory_details', 'branch_id')) {
            return;
        }

        Schema::table('inventory_details', function (Blueprint $table) {
            $table->dropColumn('branch_id');
        });
    }
};
