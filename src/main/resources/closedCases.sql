select t.bonitaInvoiceID, t.bonitaUserID, t.transactionID, t.registrationNumber, t.transactionDateTime, t.grossAmount, t.netAmount, t.postedDateTime, t.costCentre, t.transactionReason,t.fuelType,t.transactionExplanation,t.status,t.site
from Transactions t
where t.status = 'Completed'
and t.site = :s
and ( t.registrationNumber like :q
or t.claimStatus like :q
)