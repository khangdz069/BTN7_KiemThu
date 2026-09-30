using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace N7_btn
{
    [TestClass]
    public class Bai08_HuyChuoiTests
    {
        public TestContext TestContext { get; set; }

        [TestMethod]
        [DeploymentItem("data_csv\\Bai08_HuyChuoi_data.csv")]
        [DataSource("Microsoft.VisualStudio.TestTools.DataSource.CSV", "|DataDirectory|\\data_csv\\Bai08_HuyChuoi_data.csv", "Bai08_HuyChuoi_data#csv", DataAccessMethod.Sequential)]
        public void HuyChuoi_DataDriven()
        {
            MethodLibrary.MethodLibrary m = new MethodLibrary.MethodLibrary();

            string s = Convert.ToString(TestContext.DataRow[0]);
            int n = Convert.ToInt32(TestContext.DataRow[1]);
            int p = Convert.ToInt32(TestContext.DataRow[2]);
            string exp = Convert.ToString(TestContext.DataRow[3]);
            bool mongdoi_exception = Convert.ToBoolean(TestContext.DataRow[4]);
            string act;

            try
            {
                act = m.HuyChuoi(s, n, p);
            }
            catch (Exception)
            {
                Assert.IsTrue(mongdoi_exception);
                return;
            }

            Assert.IsFalse(mongdoi_exception);
            Assert.AreEqual(exp, act);
        }
    }
}
